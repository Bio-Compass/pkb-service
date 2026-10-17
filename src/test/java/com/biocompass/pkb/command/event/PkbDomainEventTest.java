package com.biocompass.pkb.command.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PkbDomainEventTest {

    @Test
    void initialEventFactoriesPopulateTheRequiredKafkaContractFields() {
        var userId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var artifactId = UUID.randomUUID();

        var events = java.util.List.of(
                PkbDomainEvent.itemCreated(userId, itemId, "workflow-1"),
                PkbDomainEvent.artifactCreated(userId, artifactId, itemId, "workflow-1"),
                PkbDomainEvent.enrichmentRequested(userId, artifactId, itemId, "workflow-1"),
                PkbDomainEvent.enrichmentCompleted(userId, artifactId, itemId, "workflow-1")
        );

        assertThat(events)
                .extracting(event -> event.eventType().value())
                .containsExactly(
                        "pkb.item.created",
                        "pkb.artifact.created",
                        "pkb.enrichment.requested",
                        "pkb.enrichment.completed"
                );
        assertThat(events).allSatisfy(event -> {
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.correlationId()).isEqualTo("workflow-1");
        });
        assertThat(events.getFirst().aggregateKey()).isEqualTo(itemId.toString());
        assertThat(events.get(1).aggregateKey()).isEqualTo(itemId.toString());
    }

    @Test
    void eventTypeRoundTripsItsWireValue() {
        assertThat(PkbDomainEventType.fromValue("pkb.enrichment.completed"))
                .isEqualTo(PkbDomainEventType.ENRICHMENT_COMPLETED);
    }
}
