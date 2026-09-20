package com.biocompass.pkb.command.event;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PkbDomainEventTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID relatedItemId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();
    private final UUID relationshipId = UUID.randomUUID();
    private final String correlationId = "test-correlation";

    @Test
    void itemCreatedEventContainsExpectedKafkaPayloadFields() {
        var event = PkbDomainEvent.itemCreated(userId, itemId, correlationId);

        assertBaseFields(event, PkbDomainEventType.ITEM_CREATED);
        assertThat(event.pkbItemId()).isEqualTo(itemId);
        assertThat(event.relatedPkbItemId()).isNull();
        assertThat(event.artifactId()).isNull();
        assertThat(event.relationshipId()).isNull();
    }

    @Test
    void itemSupersessionLinkedEventReferencesSupersededAndReplacementItems() {
        var event = PkbDomainEvent.itemSupersessionLinked(userId, itemId, relatedItemId, correlationId);

        assertBaseFields(event, PkbDomainEventType.ITEM_SUPERSESSION_LINKED);
        assertThat(event.pkbItemId()).isEqualTo(itemId);
        assertThat(event.relatedPkbItemId()).isEqualTo(relatedItemId);
    }

    @Test
    void relationshipCreatedEventContainsRelationshipId() {
        var event = PkbDomainEvent.relationshipCreated(userId, relationshipId, correlationId);

        assertBaseFields(event, PkbDomainEventType.RELATIONSHIP_CREATED);
        assertThat(event.relationshipId()).isEqualTo(relationshipId);
    }

    @Test
    void artifactCreatedEventContainsArtifactAndOptionalItemIds() {
        var event = PkbDomainEvent.artifactCreated(userId, artifactId, itemId, correlationId);

        assertBaseFields(event, PkbDomainEventType.ARTIFACT_CREATED);
        assertThat(event.artifactId()).isEqualTo(artifactId);
        assertThat(event.pkbItemId()).isEqualTo(itemId);
    }

    @Test
    void artifactCreatedEventAllowsNullItemId() {
        var event = PkbDomainEvent.artifactCreated(userId, artifactId, null, correlationId);

        assertThat(event.pkbItemId()).isNull();
        assertThat(event.artifactId()).isEqualTo(artifactId);
    }

    @Test
    void artifactAssociatedEventContainsArtifactAndItemIds() {
        var event = PkbDomainEvent.artifactAssociated(userId, artifactId, itemId, correlationId);

        assertBaseFields(event, PkbDomainEventType.ARTIFACT_ASSOCIATED);
        assertThat(event.artifactId()).isEqualTo(artifactId);
        assertThat(event.pkbItemId()).isEqualTo(itemId);
    }

    @Test
    void enrichmentRequestedEventContainsArtifactAndOptionalItemIds() {
        var event = PkbDomainEvent.enrichmentRequested(userId, artifactId, itemId, correlationId);

        assertBaseFields(event, PkbDomainEventType.ENRICHMENT_REQUESTED);
        assertThat(event.artifactId()).isEqualTo(artifactId);
        assertThat(event.pkbItemId()).isEqualTo(itemId);
    }

    @Test
    void enrichmentRequestedEventAllowsNullItemId() {
        var event = PkbDomainEvent.enrichmentRequested(userId, artifactId, null, correlationId);

        assertThat(event.pkbItemId()).isNull();
        assertThat(event.artifactId()).isEqualTo(artifactId);
    }

    @Test
    void eachEventReceivesUniqueEventIdAndTimestamp() {
        var first = PkbDomainEvent.itemCreated(userId, itemId, correlationId);
        var second = PkbDomainEvent.itemCreated(userId, itemId, correlationId);

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.occurredAt()).isNotEqualTo(second.occurredAt());
    }

    private void assertBaseFields(PkbDomainEvent event, PkbDomainEventType expectedType) {
        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo(expectedType);
        assertThat(event.eventType().value()).isEqualTo(expectedType.value());
        assertThat(event.occurredAt()).isCloseTo(Instant.now(), within(java.time.Duration.ofSeconds(1)));
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.correlationId()).isEqualTo(correlationId);
    }
}
