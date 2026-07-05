package com.biocompass.pkb.command.dto;

import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePkbRelationshipCommand(
        @NotNull(message = "userId is required")
        UUID userId,
        @NotNull(message = "fromItemId is required")
        UUID fromItemId,
        @NotNull(message = "toItemId is required")
        UUID toItemId,
        @NotBlank(message = "relationshipType is required")
        String relationshipType,
        String correlationId
) implements PkbCommand<PkbRelationshipEntity> {

    @AssertTrue(message = "relationship endpoints must be different")
    public boolean isRelationshipEndpointsValid() {
        return fromItemId == null || !fromItemId.equals(toItemId);
    }
}
