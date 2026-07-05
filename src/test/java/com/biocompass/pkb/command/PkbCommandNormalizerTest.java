package com.biocompass.pkb.command;

import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PkbCommandNormalizerTest {

    private final PkbCommandNormalizer normalizer = new PkbCommandNormalizer();

    @Test
    void normalizesItemEnvelopeWithoutMutatingPayload() {
        var command = new CreatePkbItemCommand(
                UUID.randomUUID(),
                " Nutrition_Intake ",
                " Water ",
                " ACTIVE ",
                new LinkedHashMap<>(Map.of("amount", 350, "unit", "ml")),
                " HealthKit ",
                " sample-1 ",
                null,
                null,
                null,
                " EN-US ",
                List.of(" Nutrition-Read ", "nutrition-read", " "),
                List.of(" Health ", "Nutrition"),
                " User_Reported ",
                null,
                new PkbProvenanceCommand(" HealthKit_Sync ", " System ", " workflow-1 ", " source-1 ", " extractor-1 "),
                "correlation-1"
        );

        var item = normalizer.toItemEntity(command);
        var provenance = normalizer.toProvenanceEntity(command.provenance());

        assertThat(item.getEntityType()).isEqualTo("nutrition_intake");
        assertThat(item.getSubtype()).isEqualTo("water");
        assertThat(item.getStatus()).isEqualTo("active");
        assertThat(item.getSourceType()).isEqualTo("healthkit");
        assertThat(item.getSourceId()).isEqualTo("sample-1");
        assertThat(item.getLanguage()).isEqualTo("en-us");
        assertThat(item.getConsentScope()).containsExactly("nutrition-read");
        assertThat(item.getPrivacyScope()).containsExactly("health", "nutrition");
        assertThat(item.getVerificationStatus()).isEqualTo("user_reported");
        assertThat(item.getPayload()).containsEntry("amount", 350).containsEntry("unit", "ml");
        assertThat(provenance.getSourceKind()).isEqualTo("healthkit_sync");
        assertThat(provenance.getActorType()).isEqualTo("system");
        assertThat(provenance.getWorkflowId()).isEqualTo("workflow-1");
    }

    @Test
    void normalizesRelationshipType() {
        var userId = UUID.randomUUID();
        var command = new CreatePkbRelationshipCommand(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                " Contributes-To ",
                "correlation-2"
        );

        var relationship = normalizer.toRelationshipEntity(command);

        assertThat(relationship.getUserId()).isEqualTo(userId);
        assertThat(relationship.getRelationshipType()).isEqualTo("contributes-to");
    }
}
