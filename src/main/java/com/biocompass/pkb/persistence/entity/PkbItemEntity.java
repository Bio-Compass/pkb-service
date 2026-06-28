package com.biocompass.pkb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Builder(toBuilder = true)
@Entity
@Table(name = "pkb_item")
public class PkbItemEntity {

    @Id
    @Column(name = "pkb_item_id", nullable = false)
    private UUID pkbItemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "subtype", nullable = false)
    private String subtype;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "language")
    private String language;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "consent_scope", nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private String[] consentScope = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "privacy_scope", nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private String[] privacyScope = new String[0];

    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "supersedes")
    private UUID supersedes;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (pkbItemId == null) {
            pkbItemId = UUID.randomUUID();
        }
        var now = Instant.now();
        if (ingestedAt == null) {
            ingestedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        normalizeNullableContainers();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        normalizeNullableContainers();
    }

    private void normalizeNullableContainers() {
        if (payload == null) {
            payload = new LinkedHashMap<>();
        }
        if (consentScope == null) {
            consentScope = new String[0];
        }
        if (privacyScope == null) {
            privacyScope = new String[0];
        }
    }
}
