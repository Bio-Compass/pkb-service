package com.biocompass.pkb.command.dto;

import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssociatePkbArtifactCommand(
        @NotNull(message = "userId is required")
        UUID userId,
        @NotNull(message = "artifactId is required")
        UUID artifactId,
        @NotNull(message = "pkbItemId is required")
        UUID pkbItemId,
        String correlationId
) implements PkbCommand<PkbArtifactEntity> {
}
