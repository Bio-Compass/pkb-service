package com.biocompass.pkb.command;

import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbArtifactConsentBindingCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import com.biocompass.pkb.persistence.entity.PkbArtifactProvenanceEntity;
import com.biocompass.pkb.persistence.entity.PkbConsentBindingEntity;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.biocompass.pkb.persistence.entity.PkbProvenanceEntity;
import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class PkbCommandNormalizer {

    public PkbItemEntity toItemEntity(CreatePkbItemCommand command) {
        return PkbItemEntity.builder()
                .userId(command.userId())
                .entityType(normalizeCanonicalCode(command.entityType()))
                .subtype(normalizeCanonicalCode(command.subtype()))
                .status(normalizeCanonicalCode(command.status()))
                .payload(new LinkedHashMap<>(command.payload()))
                .sourceType(normalizeCanonicalCode(command.sourceType()))
                .sourceId(normalizeFreeText(command.sourceId()))
                .observedAt(command.observedAt())
                .validFrom(command.validFrom())
                .validUntil(command.validUntil())
                .language(normalizeCanonicalCode(command.language()))
                .consentScope(normalizeScopes(command.consentScope()))
                .privacyScope(normalizeScopes(command.privacyScope()))
                .verificationStatus(normalizeCanonicalCode(command.verificationStatus()))
                .supersedes(command.supersedes())
                .build();
    }

    public PkbProvenanceEntity toProvenanceEntity(PkbProvenanceCommand command) {
        return PkbProvenanceEntity.builder()
                .sourceKind(normalizeCanonicalCode(command.sourceKind()))
                .actorType(normalizeCanonicalCode(command.actorType()))
                .workflowId(normalizeFreeText(command.workflowId()))
                .sourceReference(normalizeFreeText(command.sourceReference()))
                .extractionMethod(normalizeFreeText(command.extractionMethod()))
                .build();
    }

    public PkbRelationshipEntity toRelationshipEntity(CreatePkbRelationshipCommand command) {
        return PkbRelationshipEntity.builder()
                .userId(command.userId())
                .fromItemId(command.fromItemId())
                .toItemId(command.toItemId())
                .relationshipType(normalizeCanonicalCode(command.relationshipType()))
                .build();
    }

    public PkbArtifactEntity toArtifactEntity(RegisterPkbArtifactCommand command, String objectKey) {
        return PkbArtifactEntity.builder()
                .userId(command.userId())
                .pkbItemId(command.pkbItemId())
                .objectKey(objectKey)
                .contentType(normalizeCanonicalCode(command.contentType()))
                .sha256(normalizeCanonicalCode(command.sha256()))
                .sizeBytes(command.sizeBytes())
                .build();
    }

    public PkbArtifactProvenanceEntity toArtifactProvenanceEntity(
            PkbProvenanceCommand command,
            UUID userId,
            UUID artifactId
    ) {
        return PkbArtifactProvenanceEntity.builder()
                .userId(userId)
                .artifactId(artifactId)
                .sourceKind(normalizeCanonicalCode(command.sourceKind()))
                .actorType(normalizeCanonicalCode(command.actorType()))
                .workflowId(normalizeFreeText(command.workflowId()))
                .sourceReference(normalizeFreeText(command.sourceReference()))
                .extractionMethod(normalizeFreeText(command.extractionMethod()))
                .build();
    }

    public PkbConsentBindingEntity toArtifactConsentBindingEntity(
            PkbArtifactConsentBindingCommand command,
            UUID userId,
            UUID artifactId
    ) {
        return PkbConsentBindingEntity.builder()
                .userId(userId)
                .artifactId(artifactId)
                .consentReference(normalizeFreeText(command.consentReference()))
                .consentScope(normalizeScopes(command.consentScope()))
                .policyReference(normalizeFreeText(command.policyReference()))
                .purposeOfUse(normalizeCanonicalCode(command.purposeOfUse()))
                .validFrom(command.validFrom())
                .validUntil(command.validUntil())
                .build();
    }

    private static String[] normalizeScopes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new String[0];
        }
        return values.stream()
                .map(PkbCommandNormalizer::normalizeCanonicalCode)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toArray(String[]::new);
    }

    private static String normalizeCanonicalCode(String value) {
        var normalized = normalizeFreeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeFreeText(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
