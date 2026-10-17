package com.biocompass.pkb.command.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.biocompass.pkb.command.api.model.AssociatePkbArtifactRequest;
import com.biocompass.pkb.command.api.model.CreatePkbItemRequest;
import com.biocompass.pkb.command.api.model.CreatePkbRelationshipRequest;
import com.biocompass.pkb.command.api.model.RegisterPkbArtifactRequest;
import com.biocompass.pkb.command.api.model.SupersedePkbItemRequest;
import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbArtifactConsentBindingCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootTest(classes = PkbCommandRequestMapperTest.MapperTestApplication.class)
class PkbCommandRequestMapperTest {

    @Autowired
    private PkbItemCommandMapper itemMapper;

    @Autowired
    private PkbRelationshipCommandMapper relationshipMapper;

    @Autowired
    private PkbArtifactCommandMapper artifactMapper;

    @Test
    void mapsItemRequestsIncludingSupersessionContext() {
        var userId = UUID.randomUUID();
        var supersededItemId = UUID.randomUUID();
        var request = itemRequest();

        var createCommand = itemMapper.toCreateItemCommand(request, userId, null, "create-correlation");
        var supersedeCommand = itemMapper.toSupersedeItemCommand(
                new SupersedePkbItemRequest(request),
                userId,
                supersededItemId,
                "supersede-correlation");

        assertThat(createCommand).isEqualTo(expectedItemCommand(userId, null, "create-correlation"));
        assertThat(supersedeCommand).isEqualTo(new SupersedePkbItemCommand(
                userId,
                supersededItemId,
                expectedItemCommand(userId, supersededItemId, "supersede-correlation"),
                "supersede-correlation"));
    }

    @Test
    void mapsRelationshipRequestAndIngressContext() {
        var userId = UUID.randomUUID();
        var fromItemId = UUID.randomUUID();
        var toItemId = UUID.randomUUID();

        var command = relationshipMapper.toCreateRelationshipCommand(
                new CreatePkbRelationshipRequest(fromItemId, toItemId, "supports"),
                userId,
                "relationship-correlation");

        assertThat(command).isEqualTo(new CreatePkbRelationshipCommand(
                userId,
                fromItemId,
                toItemId,
                "supports",
                "relationship-correlation"));
    }

    @Test
    void mapsArtifactRequestsAndPathContext() {
        var userId = UUID.randomUUID();
        var pkbItemId = UUID.randomUUID();
        var artifactId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var provenance = new PkbProvenanceCommand("manual", "user", null, "note", null);
        var consentBinding = new PkbArtifactConsentBindingCommand(
                "consent-1",
                List.of("self-read"),
                "policy-1",
                "care",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"));
        var registerRequest = new RegisterPkbArtifactRequest(
                pkbItemId,
                documentId,
                "document.pdf",
                "application/pdf",
                42L,
                "a".repeat(64),
                provenance,
                consentBinding,
                true);

        var registerCommand = artifactMapper.toRegisterArtifactCommand(registerRequest, userId, "artifact-correlation");
        var associateCommand = artifactMapper.toAssociateArtifactCommand(
                new AssociatePkbArtifactRequest(pkbItemId),
                userId,
                artifactId,
                "association-correlation");

        assertThat(registerCommand).isEqualTo(new RegisterPkbArtifactCommand(
                userId,
                pkbItemId,
                documentId,
                "document.pdf",
                "application/pdf",
                42L,
                "a".repeat(64),
                provenance,
                consentBinding,
                true,
                "artifact-correlation"));
        assertThat(associateCommand).isEqualTo(new AssociatePkbArtifactCommand(
                userId,
                artifactId,
                pkbItemId,
                "association-correlation"));
    }

    private static CreatePkbItemRequest itemRequest() {
        return new CreatePkbItemRequest(
                "observation",
                "note",
                "active",
                Map.of("text", "hydration note"),
                "manual",
                "note-1",
                Instant.parse("2026-01-02T03:04:05Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                "en",
                List.of("self-read"),
                List.of("health"),
                "user_reported",
                new PkbProvenanceCommand("manual", "user", null, "note-1", null));
    }

    private static CreatePkbItemCommand expectedItemCommand(UUID userId, UUID supersedes, String correlationId) {
        var request = itemRequest();
        return new CreatePkbItemCommand(
                userId,
                request.entityType(),
                request.subtype(),
                request.status(),
                request.payload(),
                request.sourceType(),
                request.sourceId(),
                request.observedAt(),
                request.validFrom(),
                request.validUntil(),
                request.language(),
                request.consentScope(),
                request.privacyScope(),
                request.verificationStatus(),
                supersedes,
                request.provenance(),
                correlationId);
    }

    @SpringBootConfiguration
    @ComponentScan(
            basePackageClasses = PkbItemCommandMapper.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*CommandMapperImpl")
    )
    static class MapperTestApplication {
    }
}
