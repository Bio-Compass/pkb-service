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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Entity
@Table(name = "pkb_consent_binding")
public class PkbConsentBindingEntity {

    @Id
    @Column(name = "consent_binding_id", nullable = false)
    private UUID consentBindingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pkb_item_id")
    private UUID pkbItemId;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "consent_reference", nullable = false)
    private String consentReference;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "consent_scope", nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private String[] consentScope = new String[0];

    @Column(name = "policy_reference")
    private String policyReference;

    @Column(name = "purpose_of_use")
    private String purposeOfUse;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (consentBindingId == null) {
            consentBindingId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (consentScope == null) {
            consentScope = new String[0];
        }
    }
}
