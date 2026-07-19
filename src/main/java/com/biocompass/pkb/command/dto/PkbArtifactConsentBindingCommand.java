package com.biocompass.pkb.command.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record PkbArtifactConsentBindingCommand(
        @NotBlank(message = "artifactConsent.consentReference is required")
        String consentReference,
        List<@NotBlank(message = "artifactConsent.consentScope entries must not be blank") String> consentScope,
        String policyReference,
        String purposeOfUse,
        Instant validFrom,
        Instant validUntil
) {

    @AssertTrue(message = "artifactConsent validUntil must not be before validFrom")
    boolean isValidityWindowOrdered() {
        return validFrom == null || validUntil == null || !validUntil.isBefore(validFrom);
    }
}
