package com.biocompass.pkb.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public record PkbAuthorizationDecision(
        boolean allowed,
        @JsonProperty("decision_reference") String decisionReference,
        String action,
        @JsonProperty("target_user_id") UUID targetUserId,
        Map<String, Object> obligations
) {

    public PkbAuthorizationDecision {
        obligations = obligations == null ? Map.of() : Map.copyOf(obligations);
    }
}
