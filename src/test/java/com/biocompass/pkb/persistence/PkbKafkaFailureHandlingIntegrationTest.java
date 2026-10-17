package com.biocompass.pkb.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.biocompass.pkb.authorization.PkbAuthorizationDecision;
import com.biocompass.pkb.authorization.PkbAuthorizationDeniedException;
import com.biocompass.pkb.authorization.PkbAuthorizationGateway;
import com.biocompass.pkb.authorization.PkbAuthorizationInvalidDecisionException;
import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import com.biocompass.pkb.authorization.PkbAuthorizationUnavailableException;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import com.biocompass.pkb.kafka.PkbKafkaCommandMessage;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.repository.PkbProcessedCommandRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@Import(PkbKafkaFailureHandlingIntegrationTest.KafkaTopics.class)
@SpringBootTest(properties = {
        "spring.kafka.template.default-topic=pkb.commands.failure-test.v1",
        "spring.kafka.consumer.group-id=pkb-command-failure-test",
        "biocompass.pkb.kafka.command-ingress-enabled=true",
        "biocompass.pkb.kafka.dead-letter-topic=pkb.commands.failure-test.dlt.v1",
        "biocompass.pkb.kafka.replay-topic=pkb.commands.failure-test.replay.v1",
        "biocompass.pkb.kafka.retry.max-retries=2",
        "biocompass.pkb.kafka.retry.initial-interval=10ms",
        "biocompass.pkb.kafka.retry.multiplier=1.0",
        "biocompass.pkb.kafka.retry.max-interval=10ms"
})
class PkbKafkaFailureHandlingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String COMMAND_TOPIC = "pkb.commands.failure-test.v1";
    private static final String DLT_TOPIC = "pkb.commands.failure-test.dlt.v1";
    private static final String REPLAY_TOPIC = "pkb.commands.failure-test.replay.v1";

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private PkbProcessedCommandRepository processedCommandRepository;

    @Autowired
    private PkbItemDao itemDao;

    @MockitoBean
    private PkbAuthorizationGateway authorizationGateway;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void retriesTransientAuthorizationFailureThenWritesOnce() throws Exception {
        var message = commandMessage();
        var attempts = new AtomicInteger();
        when(authorizationGateway.decide(forCommand(message.commandId())))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new PkbAuthorizationUnavailableException("temporary AU failure");
                    }
                    return allowed(invocation.getArgument(0));
                });

        publish(COMMAND_TOPIC, message);

        awaitProcessed(message);
        verify(authorizationGateway, times(3)).decide(forCommand(message.commandId()));
        assertWrittenOnce(message);
    }

    @Test
    void retriesTransientDatabaseFailureThenWritesOnce() throws Exception {
        var message = commandMessage();
        when(authorizationGateway.decide(forCommand(message.commandId())))
                .thenAnswer(invocation -> allowed(invocation.getArgument(0)));
        installOneShotDatabaseFailure();

        try {
            publish(COMMAND_TOPIC, message);
            awaitProcessed(message);
        } finally {
            removeOneShotDatabaseFailure();
        }

        verify(authorizationGateway, times(2)).decide(forCommand(message.commandId()));
        assertWrittenOnce(message);
    }

    @Test
    void publishesExhaustedTransientFailureToDeadLetterTopic() throws Exception {
        var message = commandMessage();
        when(authorizationGateway.decide(forCommand(message.commandId())))
                .thenThrow(new PkbAuthorizationUnavailableException("AU remains unavailable"));

        publish(COMMAND_TOPIC, message);

        var deadLetter = awaitDeadLetter(value -> hasCommandId(value, message.commandId()));
        assertThat(deadLetter.key()).isEqualTo(message.command().userId().toString());
        verify(authorizationGateway, times(3)).decide(forCommand(message.commandId()));
        assertThat(processedCommandRepository.existsById(message.commandId())).isFalse();
        assertThat(itemDao.findAllByUser(message.command().userId())).isEmpty();
    }

    @Test
    void sendsPermanentAuthorizationFailureDirectlyToDeadLetterTopic() throws Exception {
        var message = commandMessage();
        when(authorizationGateway.decide(forCommand(message.commandId())))
                .thenThrow(new PkbAuthorizationInvalidDecisionException("AU rejected the request"));

        publish(COMMAND_TOPIC, message);

        awaitDeadLetter(value -> hasCommandId(value, message.commandId()));
        verify(authorizationGateway).decide(forCommand(message.commandId()));
        assertThat(processedCommandRepository.existsById(message.commandId())).isFalse();
        assertThat(itemDao.findAllByUser(message.command().userId())).isEmpty();
    }

    @Test
    void publishesMalformedJsonToDeadLetterTopicAndProcessesTheFollowingRecord() throws Exception {
        var malformedJson = "{not-valid-json".getBytes(UTF_8);
        var followingMessage = commandMessage();
        var processedBefore = processedCommandRepository.count();

        kafkaTemplate.send(
                COMMAND_TOPIC,
                followingMessage.command().userId().toString(),
                malformedJson
        ).get(10, TimeUnit.SECONDS);

        var deadLetter = awaitDeadLetter(value -> java.util.Arrays.equals(value, malformedJson));
        assertThat(deadLetter.value()).isEqualTo(malformedJson);
        assertThat(processedCommandRepository.count()).isEqualTo(processedBefore);
        verifyNoInteractions(authorizationGateway);

        when(authorizationGateway.decide(forCommand(followingMessage.commandId())))
                .thenAnswer(invocation -> allowed(invocation.getArgument(0)));
        publish(COMMAND_TOPIC, followingMessage);

        awaitProcessed(followingMessage);
        assertWrittenOnce(followingMessage);
        verify(authorizationGateway).decide(forCommand(followingMessage.commandId()));
    }

    @Test
    void sendsAuthorizationDenialDirectlyToDeadLetterTopicAndAcceptsReplay() throws Exception {
        var message = commandMessage();
        when(authorizationGateway.decide(forCommand(message.commandId())))
                .thenThrow(new PkbAuthorizationDeniedException("denied"))
                .thenAnswer(invocation -> allowed(invocation.getArgument(0)));

        publish(COMMAND_TOPIC, message);

        var deadLetter = awaitDeadLetter(value -> hasCommandId(value, message.commandId()));
        verify(authorizationGateway).decide(forCommand(message.commandId()));
        assertThat(processedCommandRepository.existsById(message.commandId())).isFalse();

        kafkaTemplate.send(REPLAY_TOPIC, deadLetter.key(), deadLetter.value()).get(10, TimeUnit.SECONDS);

        awaitProcessed(message);
        verify(authorizationGateway, times(2)).decide(forCommand(message.commandId()));
        assertWrittenOnce(message);
    }

    @Test
    void sendsMismatchedCommandIdReuseToTheAuditableDeadLetterPath() throws Exception {
        var original = commandMessage();
        when(authorizationGateway.decide(forCommand(original.commandId())))
                .thenAnswer(invocation -> allowed(invocation.getArgument(0)));
        publish(COMMAND_TOPIC, original);
        awaitProcessed(original);

        var changedCommand = withPayload((CreatePkbItemCommand) original.command(), Map.of("text", "changed"));
        var mismatched = new PkbKafkaCommandMessage(
                original.commandId(),
                Instant.now(),
                original.context(),
                changedCommand);
        publish(COMMAND_TOPIC, mismatched);

        var deadLetter = awaitDeadLetter(value -> hasCommandId(value, original.commandId()));
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                .contains("different user, type, or payload");
        assertWrittenOnce(original);
        verify(authorizationGateway).decide(forCommand(original.commandId()));
    }

    private void publish(String topic, PkbKafkaCommandMessage message) throws Exception {
        kafkaTemplate.send(topic, message.command().userId().toString(), message).get(10, TimeUnit.SECONDS);
    }

    private void awaitProcessed(PkbKafkaCommandMessage message) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(processedCommandRepository.existsById(message.commandId())).isTrue());
    }

    private void assertWrittenOnce(PkbKafkaCommandMessage message) {
        assertThat(processedCommandRepository.findById(message.commandId())).get()
                .extracting(processed -> processed.getDeliveryCount())
                .isEqualTo(1);
        assertThat(itemDao.findAllByUser(message.command().userId())).hasSize(1);
    }

    private ConsumerRecord<String, byte[]> awaitDeadLetter(Predicate<byte[]> expectedValue) {
        var found = new AtomicReference<ConsumerRecord<String, byte[]>>();
        try (var consumer = new KafkaConsumer<String, byte[]>(deadLetterConsumerProperties())) {
            consumer.subscribe(List.of(DLT_TOPIC));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(250)).forEach(record -> {
                    if (expectedValue.test(record.value())) {
                        found.compareAndSet(null, record);
                    }
                });
                assertThat(found.get()).isNotNull();
            });
        }
        return found.get();
    }

    private Properties deadLetterConsumerProperties() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "pkb-dlt-observer-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return properties;
    }

    private static boolean hasCommandId(byte[] value, UUID commandId) {
        return new String(value, UTF_8).contains(commandId.toString());
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    private static PkbAuthorizationRequest forCommand(UUID commandId) {
        return argThat(request -> commandId.equals(request.commandId()));
    }

    private static PkbAuthorizationDecision allowed(PkbAuthorizationRequest request) {
        return new PkbAuthorizationDecision(
                true,
                "test-au:" + request.commandId(),
                request.action(),
                request.resource().targetUserId(),
                Map.of());
    }

    private static PkbKafkaCommandMessage commandMessage() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        var actor = new PkbAuthorizationRequest.Actor(
                "actor:" + userId,
                userId,
                false,
                Set.of("user"),
                Set.of("pkb:write"),
                "self");
        var context = new PkbKafkaCommandContext("test-producer", actor, "ingress-au:" + commandId);
        var command = new CreatePkbItemCommand(
                userId,
                "observation",
                "note",
                "active",
                Map.of("text", "Kafka failure handling"),
                "integration-test",
                "source:" + commandId,
                Instant.parse("2026-09-13T10:15:30Z"),
                null,
                null,
                "en",
                List.of("health"),
                List.of("private"),
                "verified",
                null,
                new PkbProvenanceCommand("integration-test", "system", null, null, "direct"),
                "correlation:" + commandId);
        return new PkbKafkaCommandMessage(commandId, Instant.now(), context, command);
    }

    private static CreatePkbItemCommand withPayload(
            CreatePkbItemCommand command,
            Map<String, Object> payload
    ) {
        return new CreatePkbItemCommand(
                command.userId(),
                command.entityType(),
                command.subtype(),
                command.status(),
                payload,
                command.sourceType(),
                command.sourceId(),
                command.observedAt(),
                command.validFrom(),
                command.validUntil(),
                command.language(),
                command.consentScope(),
                command.privacyScope(),
                command.verificationStatus(),
                command.supersedes(),
                command.provenance(),
                command.correlationId());
    }

    private static void installOneShotDatabaseFailure() throws Exception {
        removeOneShotDatabaseFailure();
        execute("CREATE SEQUENCE pkb_test_command_failure_sequence START WITH 1");
        execute("""
                CREATE FUNCTION pkb_test_fail_first_processed_command_insert()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF nextval('pkb_test_command_failure_sequence') = 1 THEN
                        RAISE EXCEPTION 'simulated transient database failure' USING ERRCODE = '40001';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        execute("""
                CREATE TRIGGER pkb_test_fail_first_processed_command_insert
                BEFORE INSERT ON pkb_processed_command
                FOR EACH ROW
                EXECUTE FUNCTION pkb_test_fail_first_processed_command_insert()
                """);
    }

    private static void removeOneShotDatabaseFailure() throws Exception {
        execute("DROP TRIGGER IF EXISTS pkb_test_fail_first_processed_command_insert ON pkb_processed_command");
        execute("DROP FUNCTION IF EXISTS pkb_test_fail_first_processed_command_insert()");
        execute("DROP SEQUENCE IF EXISTS pkb_test_command_failure_sequence");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTopics {

        @Bean
        NewTopic commandFailureTestTopic() {
            return TopicBuilder.name(COMMAND_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic commandFailureTestDeadLetterTopic() {
            return TopicBuilder.name(DLT_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic commandFailureTestReplayTopic() {
            return TopicBuilder.name(REPLAY_TOPIC).partitions(1).replicas(1).build();
        }
    }
}
