package com.biocompass.pkb.command.dto;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreatePkbItemCommand(
        @NotNull(message = "userId is required")
        UUID userId,
        @NotBlank(message = "entityType is required")
        String entityType,
        @NotBlank(message = "subtype is required")
        String subtype,
        @NotBlank(message = "status is required")
        String status,
        @NotNull(message = "payload is required")
        Map<String, Object> payload,
        @NotBlank(message = "sourceType is required")
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
        UUID supersedes,
        @Valid
        @NotNull(message = "provenance is required")
        PkbProvenanceCommand provenance,
        String correlationId
) implements PkbCommand<PkbItemEntity> {

    public CreatePkbItemCommand {
        payload = payload == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        consentScope = consentScope == null
                ? null
                : List.copyOf(consentScope);
        privacyScope = privacyScope == null
                ? null
                : List.copyOf(privacyScope);
    }

    @AssertTrue(message = "validity validUntil must not be before validFrom")
    @JsonIgnore
    public boolean isValidityWindowValid() {
        return validFrom == null || validUntil == null || !validUntil.isBefore(validFrom);
    }

    @AssertTrue(message = "observedTimezone must be a valid IANA time-zone identifier")
    @JsonIgnore
    public boolean isObservedTimezoneValid() {
        if (observedTimezone == null) {
            return true;
        }
        try {
            var zoneId = ZoneId.of(observedTimezone);
            return !(zoneId instanceof ZoneOffset);
        } catch (DateTimeException e) {
            return false;
        }
    }

    @AssertTrue(message = "observedTimezone requires observedAt")
    @JsonIgnore
    public boolean isObservedTimezoneAccompaniedByObservedAt() {
        return observedTimezone == null || observedAt != null;
    }

    public CreatePkbItemCommand withUserIdSupersedesAndCorrelationId(
            UUID replacementUserId,
            UUID replacementSupersedes,
            String fallbackCorrelationId
    ) {
        var replacementCorrelationId = correlationId == null ? fallbackCorrelationId : correlationId;
        return new CreatePkbItemCommand(
                replacementUserId,
                entityType,
                subtype,
                status,
                payload,
                sourceType,
                sourceId,
                observedAt,
                observedTimezone,
                validFrom,
                validUntil,
                language,
                consentScope,
                privacyScope,
                verificationStatus,
                replacementSupersedes,
                provenance,
                replacementCorrelationId
        );
    }
}
