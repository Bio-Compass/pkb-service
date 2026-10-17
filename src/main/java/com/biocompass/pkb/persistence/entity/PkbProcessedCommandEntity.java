package com.biocompass.pkb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pkb_processed_command")
@Getter
@NoArgsConstructor
public class PkbProcessedCommandEntity {

    @Id
    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    @Column(name = "command_type", nullable = false, updatable = false)
    private String commandType;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "payload_hash", nullable = false, updatable = false)
    private String payloadHash;

    @Column(name = "producer_service", nullable = false, updatable = false)
    private String producerService;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "purpose_of_use", nullable = false, updatable = false)
    private String purposeOfUse;

    @Column(name = "prior_decision_reference", updatable = false)
    private String priorDecisionReference;

    @Column(name = "applied_decision_reference", nullable = false, updatable = false)
    private String appliedDecisionReference;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "last_received_at", nullable = false)
    private Instant lastReceivedAt;

    @Column(name = "delivery_count", nullable = false)
    private int deliveryCount;
}
