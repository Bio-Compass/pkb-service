package com.biocompass.pkb.command.api.model;

import java.util.UUID;

public record CreatePkbRelationshipRequest(
        UUID fromItemId,
        UUID toItemId,
        String relationshipType
) {
}
