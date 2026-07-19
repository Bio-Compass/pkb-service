package com.biocompass.pkb.command.dto;

import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record RegisterPkbArtifactCommand(
        @NotNull(message = "userId is required")
        UUID userId,
        UUID pkbItemId,
        @NotNull(message = "documentId is required")
        UUID documentId,
        @NotBlank(message = "objectName is required")
        @Pattern(
                regexp = "[a-z0-9][a-z0-9._-]{0,127}",
                message = "objectName must be a lowercase file name without path separators"
        )
        String objectName,
        @NotBlank(message = "contentType is required")
        String contentType,
        @NotNull(message = "sizeBytes is required")
        @PositiveOrZero(message = "sizeBytes must be zero or greater")
        Long sizeBytes,
        @NotBlank(message = "sha256 is required")
        @Pattern(regexp = "^[0-9A-Fa-f]{64}$", message = "sha256 must be a 64-character hex digest")
        String sha256,
        @Valid
        @NotNull(message = "provenance is required")
        PkbProvenanceCommand provenance,
        @Valid
        PkbArtifactConsentBindingCommand consentBinding,
        boolean requestEnrichment,
        String correlationId
) implements PkbCommand<PkbArtifactEntity> {
}
