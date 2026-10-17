package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.persistence.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "biocompass.pkb.eventing.enabled=true")
@Testcontainers
class PkbKafkaEventingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.0.2")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RecordingEnrichmentWorkflow enrichmentWorkflow;

    @Test
    void kafkaConsumerProcessesAnEnrichmentCompletionOnlyOnceForTheSameEventId() throws Exception {
        var event = PkbDomainEvent.enrichmentCompleted(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ocr-workflow-1"
        );

        kafkaTemplate.send(event.eventType().value(), event.aggregateKey(), event).get();
        kafkaTemplate.send(event.eventType().value(), event.aggregateKey(), event).get();

        assertThat(enrichmentWorkflow.firstDelivery().await(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(enrichmentWorkflow.secondDelivery().await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS))
                .isFalse();
        assertThat(enrichmentWorkflow.deliveries()).isEqualTo(1);
    }

    @Test
    void startupCreatesEveryConfiguredPkbEventTopic() throws Exception {
        try (var adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
        ))) {
            assertThat(adminClient.listTopics().names().get())
                    .contains(
                            "pkb.item.created",
                            "pkb.artifact.created",
                            "pkb.enrichment.requested",
                            "pkb.enrichment.completed"
                    );
        }
    }

    @TestConfiguration
    static class KafkaEventingTestConfiguration {

        @Bean
        @Primary
        RecordingEnrichmentWorkflow recordingEnrichmentWorkflow() {
            return new RecordingEnrichmentWorkflow();
        }
    }

    static class RecordingEnrichmentWorkflow implements PkbEnrichmentWorkflow {

        private final CountDownLatch firstDelivery = new CountDownLatch(1);
        private final CountDownLatch secondDelivery = new CountDownLatch(1);
        private final AtomicInteger deliveries = new AtomicInteger();

        @Override
        public void handleCompleted(PkbDomainEvent event) {
            if (deliveries.incrementAndGet() == 1) {
                firstDelivery.countDown();
            } else {
                secondDelivery.countDown();
            }
        }

        CountDownLatch firstDelivery() {
            return firstDelivery;
        }

        CountDownLatch secondDelivery() {
            return secondDelivery;
        }

        int deliveries() {
            return deliveries.get();
        }
    }
}
