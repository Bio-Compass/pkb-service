package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaPkbEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishesTheFullEventToItsEventTypeTopicUsingTheAggregateKey() {
        var event = PkbDomainEvent.artifactCreated(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "upload-1"
        );
        when(kafkaTemplate.send(eq("pkb.artifact.created"), eq(event.aggregateKey()), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new KafkaPkbEventPublisher(
                kafkaTemplate,
                new PkbEventingProperties(false, null, 50, "pkb-enrichment", 3, Duration.ofSeconds(5), null, null)
        ).publish(event);

        var payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
                eq("pkb.artifact.created"),
                eq(event.aggregateKey()),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isEqualTo(event);
    }

    @Test
    void failsTheRelayAttemptWhenKafkaDoesNotAcknowledgeBeforeTheTimeout() {
        var event = PkbDomainEvent.itemCreated(UUID.randomUUID(), UUID.randomUUID(), "create-1");
        when(kafkaTemplate.send(eq("pkb.item.created"), eq(event.aggregateKey()), any()))
                .thenReturn(new CompletableFuture<>());

        var publisher = new KafkaPkbEventPublisher(
                kafkaTemplate,
                new PkbEventingProperties(false, null, 50, "pkb-enrichment", 3, Duration.ofMillis(1), null, null)
        );

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(PkbKafkaPublicationException.class)
                .hasMessageContaining("Timed out publishing PKB event");
    }
}
