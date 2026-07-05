package com.biocompass.pkb.command.event;

import java.time.Instant;
import java.util.UUID;

public record PkbDomainEvent(
        UUID eventId,
        PkbDomainEventType eventType,
        Instant occurredAt,
        UUID userId,
        UUID pkbItemId,
        UUID relatedPkbItemId,
        UUID artifactId,
        UUID relationshipId,
        String correlationId
) {

    public static PkbDomainEvent itemCreated(UUID userId, UUID pkbItemId, String correlationId) {
        return event(PkbDomainEventType.ITEM_CREATED, userId, pkbItemId, null, null, null, correlationId);
    }

    /**
     * Emitted after a replacement item has been created and the previous item now points to it.
     * The primary item ID is the superseded item; the related item ID is the replacement.
     */
    public static PkbDomainEvent itemSupersessionLinked(
            UUID userId,
            UUID supersededItemId,
            UUID replacementItemId,
            String correlationId
    ) {
        return event(
                PkbDomainEventType.ITEM_SUPERSESSION_LINKED,
                userId,
                supersededItemId,
                replacementItemId,
                null,
                null,
                correlationId
        );
    }

    public static PkbDomainEvent relationshipCreated(UUID userId, UUID relationshipId, String correlationId) {
        return event(PkbDomainEventType.RELATIONSHIP_CREATED, userId, null, null, null, relationshipId, correlationId);
    }

    public static PkbDomainEvent artifactAssociated(UUID userId, UUID artifactId, UUID pkbItemId, String correlationId) {
        return event(PkbDomainEventType.ARTIFACT_ASSOCIATED, userId, pkbItemId, null, artifactId, null, correlationId);
    }

    private static PkbDomainEvent event(
            PkbDomainEventType eventType,
            UUID userId,
            UUID pkbItemId,
            UUID relatedPkbItemId,
            UUID artifactId,
            UUID relationshipId,
            String correlationId
    ) {
        return new PkbDomainEvent(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                userId,
                pkbItemId,
                relatedPkbItemId,
                artifactId,
                relationshipId,
                correlationId
        );
    }
}
