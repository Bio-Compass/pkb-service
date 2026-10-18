package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PkbOutboxRelayTest {

    @Mock
    private PkbOutboxEventDao outboxEventDao;

    @Mock
    private KafkaPkbEventPublisher kafkaEventPublisher;

    @Test
    void publishesAnOutboxEventAndMarksItPublished() {
        var event = PkbDomainEvent.itemCreated(UUID.randomUUID(), UUID.randomUUID(), "create-1");
        var outboxEvent = PkbOutboxEventEntity.from(event, Map.of("eventId", event.eventId().toString()));
        when(outboxEventDao.claimUnpublished(50)).thenReturn(List.of(outboxEvent));

        relay().relayUnpublishedEvents();

        verify(kafkaEventPublisher).publish(event);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        assertThat(outboxEvent.getPublishAttempts()).isEqualTo(1);
        assertThat(outboxEvent.getLastError()).isNull();
    }

    @Test
    void retainsAnOutboxEventForRetryWhenKafkaPublicationFails() {
        var event = PkbDomainEvent.artifactCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "upload-1");
        var outboxEvent = PkbOutboxEventEntity.from(event, Map.of("eventId", event.eventId().toString()));
        when(outboxEventDao.claimUnpublished(50)).thenReturn(List.of(outboxEvent));
        doThrow(new PkbKafkaPublicationException("Kafka unavailable", new IllegalStateException()))
                .when(kafkaEventPublisher).publish(event);

        relay().relayUnpublishedEvents();

        assertThat(outboxEvent.getPublishedAt()).isNull();
        assertThat(outboxEvent.getPublishAttempts()).isEqualTo(1);
        assertThat(outboxEvent.getLastError()).isEqualTo("Kafka unavailable");
    }

    private PkbOutboxRelay relay() {
        return new PkbOutboxRelay(
                outboxEventDao,
                kafkaEventPublisher,
                new PkbEventingProperties(false, null, 50, "pkb-enrichment", 3, Duration.ofSeconds(5))
        );
    }
}
