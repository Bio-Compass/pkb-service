package com.biocompass.pkb.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PkbAuthorizationRequest(
        @JsonProperty("command_id") UUID commandId,
        String action,
        @JsonProperty("producer_service") String producerService,
        Actor actor,
        Resource resource,
        @JsonProperty("prior_decision_reference") String priorDecisionReference
) {

    public record Actor(
            @NotBlank @JsonProperty("actor_id") String actorId,
            @NotNull @JsonProperty("user_id") UUID userId,
            @JsonProperty("is_staff") boolean staff,
            Set<String> roles,
            Set<String> scopes,
            @NotBlank @JsonProperty("purpose_of_use") String purposeOfUse
    ) {

        public Actor {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }
    }

    public record Resource(
            @JsonProperty("target_user_id") UUID targetUserId,
            @JsonProperty("command_type") String commandType,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("consent_scope") List<String> consentScope,
            @JsonProperty("privacy_scope") List<String> privacyScope,
            Map<String, UUID> identifiers
    ) {

        public Resource {
            consentScope = consentScope == null ? List.of() : List.copyOf(consentScope);
            privacyScope = privacyScope == null ? List.of() : List.copyOf(privacyScope);
            identifiers = identifiers == null ? Map.of() : Map.copyOf(identifiers);
        }
    }
}
