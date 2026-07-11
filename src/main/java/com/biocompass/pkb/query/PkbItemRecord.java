package com.biocompass.pkb.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PkbItemRecord(
        UUID itemId,
        UUID userId,
        String entityType,
        String subtype,
        String status,
        Map<String, Object> payload,
        String sourceType,
        String sourceId,
        OffsetDateTime observedAt,
        OffsetDateTime ingestedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String language,
        List<String> consentScope,
        List<String> privacyScope,
        String verificationStatus,
        UUID supersedes,
        UUID supersededBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
