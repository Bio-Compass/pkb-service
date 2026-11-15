package com.biocompass.pkb.command.api.model;

import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@io.swagger.v3.oas.annotations.media.Schema(additionalProperties=io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record CreatePkbItemRequest(
        String entityType,
        String subtype,
        String status,
        Map<String, Object> payload,
        String sourceType,
        String sourceId,
        Instant observedAt,
        String observedTimezone,
        Instant validFrom,
        Instant validUntil,
        String language,
        List<String> consentScope,
        List<String> privacyScope,
        String verificationStatus,
        PkbProvenanceCommand provenance
) {
}
