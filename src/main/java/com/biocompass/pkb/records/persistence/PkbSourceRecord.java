package com.biocompass.pkb.records.persistence;

import com.biocompass.pkb.payload.PkbPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PkbSourceRecord(
        UUID itemId, UUID userId, String sourceSystem, String sourceRecordId,
        UUID idempotencyKey, String fingerprint, String entityType, String subtype,
        PkbPayload payload, Instant observedAt, Instant recordedAt, String sourceType,
        List<String> privacyScope, List<String> consentScope, Instant deletedAt) {
    public PkbSourceRecord {
        java.util.Objects.requireNonNull(payload);
        privacyScope = List.copyOf(privacyScope);
        consentScope = List.copyOf(consentScope);
    }
}
