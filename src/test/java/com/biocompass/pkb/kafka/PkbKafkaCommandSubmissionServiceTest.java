package com.biocompass.pkb.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.PkbCommandValidator;
import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        classes = PkbKafkaCommandSubmissionServiceTest.TestApplication.class,
        properties = {
                "spring.kafka.template.default-topic=pkb.command.submission-test",
                "spring.kafka.consumer.auto-offset-reset=earliest"
        }
)
class PkbKafkaCommandSubmissionServiceTest {

    private static final String TOPIC = "pkb.command.submission-test";

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

    @Autowired
    private PkbCommandSubmissionGateway commandSubmissionGateway;

    @Autowired
    private RecordingKafkaConsumer recordingKafkaConsumer;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void publishesCommandThroughConfiguredJsonKafkaTemplate() throws Exception {
        var commandId = UUID.randomUUID();
        var command = createItemCommand();
        var context = new PkbKafkaCommandContext(
                "notification-service",
                new PkbAuthorizationRequest.Actor(
                        command.userId().toString(),
                        command.userId(),
                        false,
                        java.util.Set.of("user"),
                        java.util.Set.of("pkb:write"),
                        "self"),
                "decision-http");

        commandSubmissionGateway.submit(commandId, command, context);

        var message = recordingKafkaConsumer.receive();
        assertThat(message).isNotNull();
        assertThat(message.commandId()).isEqualTo(commandId);
        assertThat(message.submittedAt()).isNotNull();
        assertThat(message.context()).usingRecursiveComparison().isEqualTo(context);
        assertThat(message.command()).usingRecursiveComparison().isEqualTo(command);
    }

    private static CreatePkbItemCommand createItemCommand() {
        return new CreatePkbItemCommand(
                UUID.randomUUID(),
                "observation",
                "note",
                "active",
                Map.of("text", "hydrated"),
                "manual",
                "note-1",
                Instant.parse("2026-08-23T10:15:30Z"),
                Instant.parse("2026-08-23T10:00:00Z"),
                Instant.parse("2026-08-23T11:00:00Z"),
                "en",
                List.of("nutrition-read"),
                List.of("health"),
                "verified",
                UUID.randomUUID(),
                new PkbProvenanceCommand("manual", "user", "sync-42", "note-1", "direct"),
                "correlation-1"
        );
    }

    @SpringBootConfiguration
    @EnableKafka
    @EnableConfigurationProperties(PkbKafkaProperties.class)
    @Import({
            PkbCommandValidator.class,
            PkbKafkaConfiguration.class,
            PkbKafkaCommandSubmissionService.class,
            RecordingKafkaConsumer.class
    })
    @ImportAutoConfiguration({KafkaAutoConfiguration.class, ValidationAutoConfiguration.class})
    static class TestApplication {
    }

    static class RecordingKafkaConsumer {

        private final BlockingQueue<PkbKafkaCommandMessage> messages = new LinkedBlockingQueue<>();

        @KafkaListener(topics = TOPIC, groupId = "pkb-command-submission-test")
        void consume(PkbKafkaCommandMessage message) {
            messages.add(message);
        }

        PkbKafkaCommandMessage receive() throws InterruptedException {
            return messages.poll(10, TimeUnit.SECONDS);
        }
    }
}
