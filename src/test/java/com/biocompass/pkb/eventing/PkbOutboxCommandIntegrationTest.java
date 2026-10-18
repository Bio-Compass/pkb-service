package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.PkbCommandService;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import com.biocompass.pkb.persistence.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class PkbOutboxCommandIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PkbCommandService commandService;

    @Autowired
    private PkbOutboxEventRepository outboxEventRepository;

    @Autowired
    private PkbOutboxEventDao outboxEventDao;

    @BeforeEach
    void clearOutbox() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void committedItemWritesPersistAnUnpublishedKafkaEventInTheSameDatabase() {
        var userId = UUID.randomUUID();
        var command = new CreatePkbItemCommand(
                userId,
                "observation",
                "hydration",
                "active",
                Map.of("amount", 350, "unit", "ml"),
                "manual",
                "outbox-source-" + UUID.randomUUID(),
                Instant.parse("2026-08-23T08:30:00Z"),
                null,
                null,
                null,
                "en",
                java.util.List.of(),
                java.util.List.of(),
                "unverified",
                null,
                new PkbProvenanceCommand("manual", "user", "workflow-1", "local-test", "manual-entry"),
                "workflow-1"
        );

        var item = commandService.handle(command);

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(PkbDomainEventType.ITEM_CREATED.value());
                    assertThat(event.getUserId()).isEqualTo(userId);
                    assertThat(event.getPkbItemId()).isEqualTo(item.getPkbItemId());
                    assertThat(event.getCorrelationId()).isEqualTo("workflow-1");
                    assertThat(event.getPublishedAt()).isNull();
                    assertThat(event.getPayload()).containsKeys(
                            "eventId", "eventType", "occurredAt", "userId", "pkbItemId", "correlationId"
                    );
                });
        assertThat(outboxEventDao.claimUnpublished(10))
                .singleElement()
                .extracting(event -> event.getPkbItemId())
                .isEqualTo(item.getPkbItemId());
    }

    @Test
    void committedArtifactRegistrationPersistsArtifactAndEnrichmentEventsInTheOutbox() {
        var item = commandService.handle(createItemCommand(UUID.randomUUID(), "artifact-workflow"));
        outboxEventRepository.deleteAll();

        var artifact = commandService.handle(new RegisterPkbArtifactCommand(
                item.getUserId(),
                item.getPkbItemId(),
                UUID.randomUUID(),
                "original",
                "application/pdf",
                1024L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                new PkbProvenanceCommand("user_upload", "user", "artifact-workflow", "local-test", "direct-upload"),
                null,
                true,
                "artifact-workflow"
        ));

        assertThat(outboxEventRepository.findAll())
                .extracting(event -> event.toDomainEvent().eventType())
                .containsExactlyInAnyOrder(
                        PkbDomainEventType.ARTIFACT_CREATED,
                        PkbDomainEventType.ENRICHMENT_REQUESTED
                );
        assertThat(outboxEventRepository.findAll())
                .allSatisfy(event -> {
                    assertThat(event.getArtifactId()).isEqualTo(artifact.getArtifactId());
                    assertThat(event.getPkbItemId()).isEqualTo(item.getPkbItemId());
                    assertThat(event.getPublishedAt()).isNull();
                });
    }

    private static CreatePkbItemCommand createItemCommand(UUID userId, String workflowId) {
        return new CreatePkbItemCommand(
                userId,
                "observation",
                "hydration",
                "active",
                Map.of("amount", 350, "unit", "ml"),
                "manual",
                "outbox-source-" + UUID.randomUUID(),
                Instant.parse("2026-08-23T08:30:00Z"),
                null,
                null,
                null,
                "en",
                java.util.List.of(),
                java.util.List.of(),
                "unverified",
                null,
                new PkbProvenanceCommand("manual", "user", workflowId, "local-test", "manual-entry"),
                workflowId
        );
    }
}
