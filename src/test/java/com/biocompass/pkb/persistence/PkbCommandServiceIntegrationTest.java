package com.biocompass.pkb.persistence;

import com.biocompass.pkb.command.PkbCommandNotFoundException;
import com.biocompass.pkb.command.PkbCommandService;
import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventPublisher;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import com.biocompass.pkb.persistence.dao.PkbArtifactDao;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.dao.PkbRelationshipDao;
import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class PkbCommandServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PkbCommandService commandService;

    @Autowired
    private PkbItemDao itemDao;

    @Autowired
    private PkbRelationshipDao relationshipDao;

    @Autowired
    private PkbArtifactDao artifactDao;

    @Autowired
    private RecordingPkbDomainEventPublisher eventPublisher;

    @BeforeEach
    void clearEvents() {
        eventPublisher.clear();
    }

    @Test
    void createItemPersistsItemAndProvenanceThenPublishesEventAfterCommit() {
        var userId = UUID.randomUUID();
        var command = waterIntakeCommand(userId, "integration-create", "integration-source-1");

        var savedItem = commandService.handle(command);

        assertThat(itemDao.findByUserAndItemId(userId, savedItem.getPkbItemId()))
                .isPresent()
                .get()
                .satisfies(item -> {
                    assertThat(item.getEntityType()).isEqualTo("nutrition_intake");
                    assertThat(item.getSubtype()).isEqualTo("water");
                    assertThat(item.getPayload()).containsEntry("amount", 350);
                    assertThat(item.getConsentScope()).containsExactly("nutrition-read");
                    assertThat(item.getPrivacyScope()).containsExactly("nutrition", "health");
                });
        assertThat(itemDao.findProvenance(savedItem.getPkbItemId()))
                .singleElement()
                .satisfies(provenance -> {
                    assertThat(provenance.getSourceKind()).isEqualTo("healthkit_sync");
                    assertThat(provenance.getWorkflowId()).isEqualTo("workflow-integration-source-1");
                });
        assertThat(eventPublisher.events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo(PkbDomainEventType.ITEM_CREATED);
                    assertThat(event.userId()).isEqualTo(userId);
                    assertThat(event.pkbItemId()).isEqualTo(savedItem.getPkbItemId());
                    assertThat(event.correlationId()).isEqualTo("integration-create");
                });
    }

    @Test
    void supersedeItemCreatesReplacementAndLinksBothItems() {
        var userId = UUID.randomUUID();
        var originalItem = commandService.handle(waterIntakeCommand(userId, "original", "original-source"));
        eventPublisher.clear();

        var replacementCommand = waterIntakeCommand(userId, "replacement", "replacement-source");
        var replacementItem = commandService.handle(new SupersedePkbItemCommand(
                userId,
                originalItem.getPkbItemId(),
                replacementCommand,
                "supersede-correlation"
        ));

        assertThat(itemDao.findByUserAndItemId(userId, originalItem.getPkbItemId()))
                .isPresent()
                .get()
                .extracting(PkbItemEntity::getSupersededBy)
                .isEqualTo(replacementItem.getPkbItemId());
        assertThat(itemDao.findByUserAndItemId(userId, replacementItem.getPkbItemId()))
                .isPresent()
                .get()
                .extracting(PkbItemEntity::getSupersedes)
                .isEqualTo(originalItem.getPkbItemId());
        assertThat(eventPublisher.events())
                .extracting(PkbDomainEvent::eventType)
                .containsExactly(PkbDomainEventType.ITEM_SUPERSESSION_LINKED, PkbDomainEventType.ITEM_CREATED);
        assertThat(eventPublisher.events().getFirst())
                .satisfies(event -> {
                    assertThat(event.pkbItemId()).isEqualTo(originalItem.getPkbItemId());
                    assertThat(event.relatedPkbItemId()).isEqualTo(replacementItem.getPkbItemId());
                });
    }

    @Test
    void createRelationshipRequiresOwnedEndpointsAndPublishesEvent() {
        var userId = UUID.randomUUID();
        var sourceItem = commandService.handle(waterIntakeCommand(userId, "source", "relationship-source"));
        var targetItem = commandService.handle(waterSummaryCommand(userId));
        eventPublisher.clear();

        var relationship = commandService.handle(new CreatePkbRelationshipCommand(
                userId,
                sourceItem.getPkbItemId(),
                targetItem.getPkbItemId(),
                "Contributes-To",
                "relationship-correlation"
        ));

        assertThat(relationshipDao.findOutgoing(userId, sourceItem.getPkbItemId()))
                .singleElement()
                .satisfies(savedRelationship -> {
                    assertThat(savedRelationship.getRelationshipId()).isEqualTo(relationship.getRelationshipId());
                    assertThat(savedRelationship.getRelationshipType()).isEqualTo("contributes-to");
                    assertThat(savedRelationship.getToItemId()).isEqualTo(targetItem.getPkbItemId());
                });
        assertThat(eventPublisher.events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo(PkbDomainEventType.RELATIONSHIP_CREATED);
                    assertThat(event.relationshipId()).isEqualTo(relationship.getRelationshipId());
                });
    }

    @Test
    void failedRelationshipCreationDoesNotPublishEvent() {
        var userId = UUID.randomUUID();
        var sourceItem = commandService.handle(waterIntakeCommand(userId, "source", "failed-source"));
        eventPublisher.clear();

        assertThatThrownBy(() -> commandService.handle(new CreatePkbRelationshipCommand(
                userId,
                sourceItem.getPkbItemId(),
                UUID.randomUUID(),
                "supports",
                "failed-relationship"
        )))
                .isInstanceOf(PkbCommandNotFoundException.class);

        assertThat(eventPublisher.events()).isEmpty();
    }

    @Test
    void associateArtifactLinksExistingArtifactToOwnedItemAndPublishesEvent() {
        var userId = UUID.randomUUID();
        var item = commandService.handle(waterIntakeCommand(userId, "artifact-item", "artifact-item-source"));
        var artifact = artifactDao.save(PkbArtifactEntity.builder()
                .userId(userId)
                .objectKey("users/%s/documents/%s/original.json".formatted(userId, UUID.randomUUID()))
                .contentType("application/json")
                .sha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .sizeBytes(512L)
                .build());
        eventPublisher.clear();

        var associatedArtifact = commandService.handle(new AssociatePkbArtifactCommand(
                userId,
                artifact.getArtifactId(),
                item.getPkbItemId(),
                "artifact-correlation"
        ));

        assertThat(artifactDao.findByUserAndArtifactId(userId, associatedArtifact.getArtifactId()))
                .isPresent()
                .get()
                .extracting(PkbArtifactEntity::getPkbItemId)
                .isEqualTo(item.getPkbItemId());
        assertThat(eventPublisher.events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo(PkbDomainEventType.ARTIFACT_ASSOCIATED);
                    assertThat(event.artifactId()).isEqualTo(artifact.getArtifactId());
                    assertThat(event.pkbItemId()).isEqualTo(item.getPkbItemId());
                });
    }

    private static CreatePkbItemCommand waterIntakeCommand(
            UUID userId,
            String correlationId,
            String sourceId
    ) {
        var observedAt = Instant.parse("2026-06-20T06:15:00Z");
        return new CreatePkbItemCommand(
                userId,
                "Nutrition_Intake",
                "Water",
                "Active",
                new LinkedHashMap<>(Map.of(
                        "substance", "water",
                        "amount", 350,
                        "unit", "ml",
                        "start_time", "2026-06-20T08:15:00+02:00",
                        "end_time", "2026-06-20T08:15:00+02:00"
                )),
                "HealthKit",
                sourceId,
                observedAt,
                observedAt,
                observedAt,
                null,
                List.of("Nutrition-Read"),
                List.of("Nutrition", "Health"),
                "User_Reported",
                null,
                new PkbProvenanceCommand(
                        "HealthKit_Sync",
                        "System",
                        "workflow-%s".formatted(sourceId),
                        sourceId,
                        "healthkit-water-intake-import-v1"
                ),
                correlationId
        );
    }

    private static CreatePkbItemCommand waterSummaryCommand(UUID userId) {
        return new CreatePkbItemCommand(
                userId,
                "Nutrition_Summary",
                "Daily_Water_Total",
                "Active",
                new LinkedHashMap<>(Map.of(
                        "amount", 350,
                        "unit", "ml",
                        "date", "2026-06-20"
                )),
                "Derived",
                "relationship-target",
                Instant.parse("2026-06-20T23:59:59Z"),
                Instant.parse("2026-06-20T00:00:00Z"),
                Instant.parse("2026-06-20T23:59:59Z"),
                null,
                List.of("Nutrition-Read"),
                List.of("Nutrition", "Health"),
                "Derived",
                null,
                new PkbProvenanceCommand("Derived", "System", "workflow-%s".formatted("relationship-target"), "relationship-target", "daily-rollup-v1"),
                "target"
        );
    }

    @TestConfiguration
    static class EventPublisherTestConfiguration {

        @Bean
        @Primary
        RecordingPkbDomainEventPublisher recordingPkbDomainEventPublisher() {
            return new RecordingPkbDomainEventPublisher();
        }
    }

    static class RecordingPkbDomainEventPublisher implements PkbDomainEventPublisher {

        private final List<PkbDomainEvent> events = new ArrayList<>();

        @Override
        public void publish(PkbDomainEvent event) {
            events.add(event);
        }

        List<PkbDomainEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
