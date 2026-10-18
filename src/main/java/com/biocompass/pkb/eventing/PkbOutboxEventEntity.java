package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "pkb_outbox_event")
public class PkbOutboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pkb_item_id")
    private UUID pkbItemId;

    @Column(name = "related_pkb_item_id")
    private UUID relatedPkbItemId;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "relationship_id")
    private UUID relationshipId;

    @Column(name = "correlation_id")
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    @Builder.Default
    private int publishAttempts = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PkbOutboxEventEntity from(PkbDomainEvent event, Map<String, Object> payload) {
        return builder()
                .eventId(event.eventId())
                .eventType(event.eventType().value())
                .occurredAt(event.occurredAt())
                .userId(event.userId())
                .pkbItemId(event.pkbItemId())
                .relatedPkbItemId(event.relatedPkbItemId())
                .artifactId(event.artifactId())
                .relationshipId(event.relationshipId())
                .correlationId(event.correlationId())
                .payload(new LinkedHashMap<>(payload))
                .createdAt(Instant.now())
                .build();
    }

    public PkbDomainEvent toDomainEvent() {
        return new PkbDomainEvent(
                eventId,
                PkbDomainEventType.fromValue(eventType),
                occurredAt,
                userId,
                pkbItemId,
                relatedPkbItemId,
                artifactId,
                relationshipId,
                correlationId
        );
    }

    public void markPublished() {
        publishedAt = Instant.now();
        publishAttempts++;
        lastError = null;
    }

    public void markFailed(String error) {
        publishAttempts++;
        lastError = error;
    }
}
