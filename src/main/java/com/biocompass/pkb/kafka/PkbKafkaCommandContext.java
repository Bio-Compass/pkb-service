package com.biocompass.pkb.kafka;

import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PkbKafkaCommandContext(
        @NotBlank String producerService,
        @Valid @NotNull PkbAuthorizationRequest.Actor actor,
        String priorDecisionReference
) {

    public PkbKafkaCommandContext withPriorDecisionReference(String decisionReference) {
        return new PkbKafkaCommandContext(producerService, actor, decisionReference);
    }
}
