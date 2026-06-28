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
@Table(name = "pkb_relationship")
public class PkbRelationshipEntity {

    @Id
    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "from_item_id", nullable = false)
    private UUID fromItemId;

    @Column(name = "to_item_id", nullable = false)
    private UUID toItemId;

    @Column(name = "relationship_type", nullable = false)
    private String relationshipType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (relationshipId == null) {
            relationshipId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
