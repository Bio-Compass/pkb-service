package com.biocompass.pkb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Entity
@Table(name = "pkb_provenance")
public class PkbProvenanceEntity {

    @Id
    @Column(name = "provenance_id", nullable = false)
    private UUID provenanceId;

    @Column(name = "pkb_item_id", nullable = false)
    private UUID pkbItemId;

    @Column(name = "source_kind", nullable = false)
    private String sourceKind;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "extraction_method")
    private String extractionMethod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (provenanceId == null) {
            provenanceId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
