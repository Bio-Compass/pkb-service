package com.biocompass.pkb.command.api.model;

import com.biocompass.pkb.command.dto.PkbArtifactConsentBindingCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import java.util.UUID;

public record RegisterPkbArtifactRequest(
        UUID pkbItemId,
        UUID documentId,
        String objectName,
        String contentType,
        Long sizeBytes,
        String sha256,
        PkbProvenanceCommand provenance,
        PkbArtifactConsentBindingCommand consentBinding,
        boolean requestEnrichment
) {
}
