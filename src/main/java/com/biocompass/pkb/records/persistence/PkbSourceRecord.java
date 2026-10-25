package com.biocompass.pkb.records.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PkbSourceRecord(
        UUID itemId, UUID userId, String sourceSystem, String sourceRecordId,
        UUID idempotencyKey, String fingerprint, String entityType, String subtype,
        BigDecimal value, String unit, Instant observedAt, Instant recordedAt, String sourceType,
        List<String> privacyScope, List<String> consentScope, Instant deletedAt) {
    public PkbSourceRecord {
        privacyScope = List.copyOf(privacyScope);
        consentScope = List.copyOf(consentScope);
    }
}
