package com.biocompass.pkb.command.dto;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SupersedePkbItemCommand(
        @NotNull(message = "userId is required")
        UUID userId,
        @NotNull(message = "supersededItemId is required")
        UUID supersededItemId,
        @Valid
        @NotNull(message = "replacementItem is required")
        CreatePkbItemCommand replacementItem,
        String correlationId
) implements PkbCommand<PkbItemEntity> {

    @AssertTrue(message = "replacementItem.userId must match userId")
    public boolean isReplacementUserIdValid() {
        return userId == null
                || replacementItem == null
                || replacementItem.userId() == null
                || userId.equals(replacementItem.userId());
    }

    @AssertTrue(message = "replacementItem.supersedes must match supersededItemId")
    public boolean isReplacementSupersedesValid() {
        return supersededItemId == null
                || replacementItem == null
                || replacementItem.supersedes() == null
                || supersededItemId.equals(replacementItem.supersedes());
    }
}
