package com.biocompass.pkb.command.api.model;

import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CreatePkbItemRequest(
        String entityType,
        String subtype,
        String status,
        Map<String, Object> payload,
        String sourceType,
        String sourceId,
        Instant observedAt,
        Instant validFrom,
        Instant validUntil,
        String language,
        List<String> consentScope,
        List<String> privacyScope,
        String verificationStatus,
        PkbProvenanceCommand provenance
) {
}
