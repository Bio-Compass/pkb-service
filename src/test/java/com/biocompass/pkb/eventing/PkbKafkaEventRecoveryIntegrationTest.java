package com.biocompass.pkb.eventing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

import com.biocompass.pkb.authorization.*;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.kafka.*;
import com.biocompass.pkb.persistence.AbstractPostgresIntegrationTest;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "biocompass.pkb.eventing.enabled=true",
        "biocompass.pkb.eventing.dead-letter-topic=pkb.events.recovery.dlt.v1",
        "biocompass.pkb.eventing.replay-topic=pkb.events.recovery.replay.v1",
        "biocompass.pkb.kafka.command-ingress-enabled=true",
        "spring.kafka.template.default-topic=pkb.commands.recovery.v1",
        "biocompass.pkb.kafka.dead-letter-topic=pkb.commands.recovery.dlt.v1",
        "biocompass.pkb.kafka.replay-topic=pkb.commands.recovery.replay.v1",
        "biocompass.pkb.kafka.retry.max-retries=2",
        "biocompass.pkb.kafka.retry.initial-interval=10ms",
        "biocompass.pkb.kafka.retry.max-interval=10ms"
})
class PkbKafkaEventRecoveryIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String EVENTS = "pkb.enrichment.completed";
    private static final String EVENT_DLT = "pkb.events.recovery.dlt.v1";
    private static final String EVENT_REPLAY = "pkb.events.recovery.replay.v1";
    private static final String COMMANDS = "pkb.commands.recovery.v1";
    private static final String COMMAND_DLT = "pkb.commands.recovery.dlt.v1";
    private static final String COMMAND_REPLAY = "pkb.commands.recovery.replay.v1";
    private static final String TRACE_HEADER = "recovery-test-trace";

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Binding normalizes whitespace; listener subscription must use that same value.
        registry.add("biocompass.pkb.eventing.replay-topic", () -> " " + EVENT_REPLAY + " ");
    }

    @Autowired KafkaTemplate<String, Object> template;
    @Autowired RecordingWorkflow workflow;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PkbAuthorizationGateway authorization;
    @MockitoSpyBean PkbKafkaCommandProcessor commandProcessor;

    @BeforeEach
    void createEffectTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS pkb_recovery_test_effect (event_id UUID PRIMARY KEY)");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedEventRecoversOnlyToEventDltThenReplaysExactlyOnce(boolean transientFailure) throws Exception {
        var event = event();
        workflow.failures.put(event.eventId(), transientFailure
                ? new TransientDataAccessResourceException("temporary workflow failure")
                : new IllegalArgumentException("permanent workflow failure"));
        var record = publish(EVENTS, event.aggregateKey(), event);
        var dead = awaitDlt(EVENT_DLT, event.aggregateKey(), 1);
        assertThat(workflow.attempts(event.eventId())).isEqualTo(transientFailure ? 3 : 1);
        assertThat(processed(event.eventId())).isZero();
        assertThat(effects(event.eventId())).isZero();
        assertMetadata(dead, EVENTS, record.offset());
        assertThat(new String(dead.value(), UTF_8)).contains(event.eventId().toString());
        assertNoDlt(COMMAND_DLT, event.aggregateKey());

        workflow.failures.remove(event.eventId());
        replay(EVENT_REPLAY, dead);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(processed(event.eventId())).isEqualTo(1);
            assertThat(effects(event.eventId())).isEqualTo(1);
        });
        int successfulAttempts = workflow.attempts(event.eventId());
        // A later successful event on the same replay partition proves duplicate replay was consumed.
        replay(EVENT_REPLAY, dead);
        var following = event();
        publish(EVENT_REPLAY, following.aggregateKey(), following);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(processed(following.eventId())).isEqualTo(1));
        assertThat(workflow.attempts(event.eventId())).isEqualTo(successfulAttempts);
        assertThat(effects(event.eventId())).isEqualTo(1);
        assertThat(commandCount(event.userId())).isZero();
        assertNoDlt(COMMAND_DLT, event.aggregateKey());
        verifyNoInteractions(commandProcessor);
    }

    @Test
    void repeatedReplayFailureReturnsToEventDlt() throws Exception {
        var event = event();
        workflow.failures.put(event.eventId(), new IllegalArgumentException("still invalid"));
        publish(EVENTS, event.aggregateKey(), event);
        var first = awaitDlt(EVENT_DLT, event.aggregateKey(), 1);
        replay(EVENT_REPLAY, first);
        var second = awaitDlt(EVENT_DLT, event.aggregateKey(), 2);
        assertThat(workflow.attempts(event.eventId())).isEqualTo(2);
        assertThat(processed(event.eventId())).isZero();
        assertThat(effects(event.eventId())).isZero();
        assertThat(second.headers().headers(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotEmpty();
        assertNoDlt(COMMAND_DLT, event.aggregateKey());
        verifyNoInteractions(commandProcessor);
    }

    @Test
    void malformedEventBytesRemainInEventDltAndDoNotBlockFollowingEvents() throws Exception {
        var key = UUID.randomUUID().toString();
        var bytes = "{invalid-event-json".getBytes(UTF_8);
        var original = publish(EVENTS, key, bytes);
        var dead = awaitDlt(EVENT_DLT, key, 1);
        assertThat(dead.value()).isEqualTo(bytes);
        assertMetadata(dead, EVENTS, original.offset());
        var following = event();
        publish(EVENTS, following.aggregateKey(), following);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(processed(following.eventId())).isEqualTo(1));
        assertNoDlt(COMMAND_DLT, key);
        verifyNoInteractions(commandProcessor);
    }

    @Test
    void commandFailureAndReplayStayOnCommandPath() throws Exception {
        var user = UUID.randomUUID();
        var actor = new PkbAuthorizationRequest.Actor(user.toString(), user, false, Set.of(), Set.of(), "self");
        var command = new CreatePkbItemCommand(user, "observation", "note", "active", Map.of("text", "recovery"),
                "manual", UUID.randomUUID().toString(), null, null, null, null, "en", List.of(), List.of(),
                "unverified", null, new PkbProvenanceCommand("manual", "user", null, null, "direct"), "recovery");
        var message = new PkbKafkaCommandMessage(UUID.randomUUID(), Instant.now(),
                new PkbKafkaCommandContext("pkb-service", actor, null), command);
        when(authorization.decide(any())).thenThrow(new PkbAuthorizationDeniedException("denied"));
        publish(COMMANDS, user.toString(), message);
        var dead = awaitDlt(COMMAND_DLT, user.toString(), 1);
        assertThat(commandCount(user)).isZero();
        assertNoDlt(EVENT_DLT, user.toString());
        doAnswer(call -> {
            PkbAuthorizationRequest request = call.getArgument(0);
            return new PkbAuthorizationDecision(true, "replay-allowed", request.action(), request.resource().targetUserId(), Map.of());
        }).when(authorization).decide(any());
        replay(COMMAND_REPLAY, dead);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(commandCount(user)).isEqualTo(1));
        assertThat(workflow.attempts.containsKey(message.commandId())).isFalse();
        assertNoDlt(EVENT_DLT, user.toString());
    }

    private org.apache.kafka.clients.producer.RecordMetadata publish(String topic, String key, Object value) throws Exception {
        var record = new ProducerRecord<String, Object>(topic, 1, key, value);
        record.headers().add(TRACE_HEADER, key.getBytes(UTF_8));
        return template.send(record).get(10, TimeUnit.SECONDS).getRecordMetadata();
    }

    private void replay(String topic, ConsumerRecord<String, byte[]> dead) throws Exception {
        template.send(new ProducerRecord<String, Object>(topic, dead.partition(), null,
                dead.key(), dead.value(), dead.headers())).get(10, TimeUnit.SECONDS);
    }

    private ConsumerRecord<String, byte[]> awaitDlt(String topic, String key, int count) {
        var found = new ArrayList<ConsumerRecord<String, byte[]>>();
        try (var consumer = observer(topic)) {
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(100)).forEach(r -> { if (key.equals(r.key())) found.add(r); });
                assertThat(found).hasSizeGreaterThanOrEqualTo(count);
            });
        }
        return found.get(count - 1);
    }

    private void assertNoDlt(String topic, String key) {
        try (var consumer = observer(topic)) {
            var ends = consumer.endOffsets(consumer.assignment());
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(100)).forEach(r -> assertThat(r.key()).isNotEqualTo(key));
                for (var partition : consumer.assignment()) assertThat(consumer.position(partition)).isGreaterThanOrEqualTo(ends.get(partition));
            });
        }
    }

    private KafkaConsumer<String, byte[]> observer(String topic) {
        var consumer = new KafkaConsumer<String, byte[]>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false));
        consumer.assign(List.of(new TopicPartition(topic, 0), new TopicPartition(topic, 1), new TopicPartition(topic, 2)));
        consumer.seekToBeginning(consumer.assignment());
        return consumer;
    }

    private static void assertMetadata(ConsumerRecord<String, byte[]> dead, String source, long offset) {
        assertThat(dead.partition()).isEqualTo(1);
        assertThat(new String(dead.headers().lastHeader(TRACE_HEADER).value(), UTF_8)).isEqualTo(dead.key());
        assertThat(new String(dead.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(), UTF_8)).isEqualTo(source);
        assertThat(ByteBuffer.wrap(dead.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION).value()).getInt()).isEqualTo(1);
        assertThat(ByteBuffer.wrap(dead.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value()).getLong()).isEqualTo(offset);
        assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).isNotNull();
    }

    private int processed(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM pkb_processed_event WHERE event_id = ?", Integer.class, id);
    }
    private int effects(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM pkb_recovery_test_effect WHERE event_id = ?", Integer.class, id);
    }
    private int commandCount(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM pkb_processed_command WHERE user_id = ?", Integer.class, user);
    }
    private static PkbDomainEvent event() {
        return PkbDomainEvent.enrichmentCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "recovery");
    }

    @TestConfiguration
    static class Configuration {
        @Bean RecordingWorkflow recordingWorkflow(JdbcTemplate jdbc) { return new RecordingWorkflow(jdbc); }
        @Bean KafkaAdmin.NewTopics commandTopics() {
            return new KafkaAdmin.NewTopics(TopicBuilder.name(COMMANDS).partitions(3).replicas(1).build(),
                    TopicBuilder.name(COMMAND_DLT).partitions(3).replicas(1).build(),
                    TopicBuilder.name(COMMAND_REPLAY).partitions(3).replicas(1).build());
        }
    }
    static class RecordingWorkflow implements PkbEnrichmentWorkflow {
        final Map<UUID, RuntimeException> failures = new ConcurrentHashMap<>();
        final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
        final JdbcTemplate jdbc;
        RecordingWorkflow(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        int attempts(UUID id) { return attempts.getOrDefault(id, new AtomicInteger()).get(); }
        public void handleCompleted(PkbDomainEvent event) {
            attempts.computeIfAbsent(event.eventId(), key -> new AtomicInteger()).incrementAndGet();
            jdbc.update("INSERT INTO pkb_recovery_test_effect (event_id) VALUES (?)", event.eventId());
            var failure = failures.get(event.eventId());
            if (failure != null) throw failure;
        }
    }
}
