package com.biocompass.pkb.command.dto;

import jakarta.validation.constraints.NotBlank;

public record PkbProvenanceCommand(
        @NotBlank(message = "provenance.sourceKind is required")
        String sourceKind,
        String actorType,
        String workflowId,
        String sourceReference,
        String extractionMethod
) {
}
