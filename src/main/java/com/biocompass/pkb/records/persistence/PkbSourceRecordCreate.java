package com.biocompass.pkb.records.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PkbSourceRecordCreate(
        UUID itemId, UUID userId, String producer, String sourceRecordId,
        UUID idempotencyKey, String fingerprint, String entityType, String subtype,
        BigDecimal value, String unit, Instant observedAt, Instant recordedAt, String sourceType,
        List<String> privacyScope, List<String> consentScope, String workflowId) {
    public PkbSourceRecordCreate {
        privacyScope = List.copyOf(privacyScope);
        consentScope = List.copyOf(consentScope);
    }
}
