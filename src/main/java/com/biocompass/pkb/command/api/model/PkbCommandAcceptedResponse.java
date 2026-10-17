package com.biocompass.pkb.command.api.model;

import java.util.UUID;

public record PkbCommandAcceptedResponse(UUID commandId, String status, String correlationId) {

    public static PkbCommandAcceptedResponse accepted(UUID commandId, String correlationId) {
        return new PkbCommandAcceptedResponse(commandId, "accepted", correlationId);
    }
}
