package com.biocompass.pkb.command;

import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PkbCommandValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsMinimalValidCreateCommand() {
        assertThat(validator.validate(validCreateCommand(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void rejectsInvalidCreateCommandEnvelope() {
        var command = new CreatePkbItemCommand(
                null,
                " ",
                "",
                null,
                null,
                " ",
                null,
                null,
                Instant.parse("2026-06-21T00:00:00Z"),
                Instant.parse("2026-06-20T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                new PkbProvenanceCommand(" ", null, null, null, null),
                null
        );

        assertThat(validationMessages(command))
                .contains(
                        "userId is required",
                        "entityType is required",
                        "subtype is required",
                        "status is required",
                        "payload is required",
                        "sourceType is required",
                        "validity validUntil must not be before validFrom",
                        "provenance.sourceKind is required"
                );
    }

    @Test
    void rejectsSelfRelationship() {
        var itemId = UUID.randomUUID();
        var command = new CreatePkbRelationshipCommand(UUID.randomUUID(), itemId, itemId, "supports", null);

        assertThat(validationMessages(command)).contains("relationship endpoints must be different");
    }

    @Test
    void rejectsConflictingSupersessionReplacementMetadata() {
        var userId = UUID.randomUUID();
        var command = new SupersedePkbItemCommand(
                userId,
                UUID.randomUUID(),
                validCreateCommand(UUID.randomUUID()),
                "correlation"
        );

        assertThat(validationMessages(command)).contains("replacementItem.userId must match userId");
    }

    @Test
    void rejectsIncompleteArtifactAssociationCommand() {
        var command = new AssociatePkbArtifactCommand(UUID.randomUUID(), null, null, null);

        assertThat(validationMessages(command))
                .contains("artifactId is required", "pkbItemId is required");
    }

    private List<String> validationMessages(Object command) {
        return validator.validate(command).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    private static CreatePkbItemCommand validCreateCommand(UUID userId) {
        return new CreatePkbItemCommand(
                userId,
                "observation",
                "note",
                "active",
                new LinkedHashMap<>(Map.of("text", "headache")),
                "manual",
                "manual-note-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new PkbProvenanceCommand("manual", "user", null, null, null),
                "correlation"
        );
    }
}
