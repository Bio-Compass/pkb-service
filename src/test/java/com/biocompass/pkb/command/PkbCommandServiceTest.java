package com.biocompass.pkb.command;

import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventPublisher;
import com.biocompass.pkb.command.event.PkbDomainEventType;
import com.biocompass.pkb.command.handler.AssociatePkbArtifactCommandHandler;
import com.biocompass.pkb.command.handler.CreatePkbItemCommandHandler;
import com.biocompass.pkb.command.handler.CreatePkbRelationshipCommandHandler;
import com.biocompass.pkb.command.handler.PkbAfterCommitEventPublisher;
import com.biocompass.pkb.command.handler.PkbCommandItemResolver;
import com.biocompass.pkb.command.handler.SupersedePkbItemCommandHandler;
import com.biocompass.pkb.persistence.dao.PkbArtifactDao;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.dao.PkbRelationshipDao;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PkbCommandServiceTest {

    @Mock
    private PkbItemDao itemDao;

    @Mock
    private PkbRelationshipDao relationshipDao;

    @Mock
    private PkbArtifactDao artifactDao;

    @Mock
    private PkbDomainEventPublisher eventPublisher;

    private PkbCommandService commandService;

    @BeforeEach
    void setUp() {
        var normalizer = new PkbCommandNormalizer();
        var itemResolver = new PkbCommandItemResolver(itemDao);
        var afterCommitEventPublisher = new PkbAfterCommitEventPublisher(eventPublisher);
        var createItemHandler = new CreatePkbItemCommandHandler(
                itemDao,
                normalizer,
                itemResolver,
                afterCommitEventPublisher
        );
        commandService = new PkbCommandService(List.of(
                createItemHandler,
                new SupersedePkbItemCommandHandler(createItemHandler),
                new CreatePkbRelationshipCommandHandler(
                        relationshipDao,
                        normalizer,
                        itemResolver,
                        afterCommitEventPublisher
                ),
                new AssociatePkbArtifactCommandHandler(
                        artifactDao,
                        itemResolver,
                        afterCommitEventPublisher
                )
        ));
    }

    @Test
    void createItemPersistsNormalizedItemWithProvenanceAndPublishesEvent() {
        var itemId = UUID.randomUUID();
        var command = waterIntakeCommand(UUID.randomUUID());
        when(itemDao.saveWithProvenance(any(), any())).thenAnswer(invocation -> {
            PkbItemEntity item = invocation.getArgument(0);
            item.setPkbItemId(itemId);
            return item;
        });

        var savedItem = commandService.handle(command);

        assertThat(savedItem.getPkbItemId()).isEqualTo(itemId);
        var itemCaptor = ArgumentCaptor.forClass(PkbItemEntity.class);
        verify(itemDao).saveWithProvenance(itemCaptor.capture(), any());
        assertThat(itemCaptor.getValue().getEntityType()).isEqualTo("nutrition_intake");
        assertThat(itemCaptor.getValue().getConsentScope()).containsExactly("nutrition-read");

        var eventCaptor = ArgumentCaptor.forClass(PkbDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(PkbDomainEventType.ITEM_CREATED);
        assertThat(eventCaptor.getValue().pkbItemId()).isEqualTo(itemId);
        assertThat(eventCaptor.getValue().correlationId()).isEqualTo("correlation-create");
    }

    @Test
    void missingRelationshipEndpointDoesNotPersistOrPublishEvent() {
        var userId = UUID.randomUUID();
        var fromItemId = UUID.randomUUID();
        var toItemId = UUID.randomUUID();
        var command = new CreatePkbRelationshipCommand(userId, fromItemId, toItemId, "supports", "correlation-rel");
        when(itemDao.existsByUserAndItemId(userId, fromItemId)).thenReturn(true);
        when(itemDao.existsByUserAndItemId(userId, toItemId)).thenReturn(false);

        assertThatThrownBy(() -> commandService.handle(command))
                .isInstanceOf(PkbCommandNotFoundException.class)
                .hasMessageContaining("PKB item not found");
        verifyNoInteractions(relationshipDao, artifactDao, eventPublisher);
    }

    @Test
    void createRelationshipPublishesEventAfterSaving() {
        var userId = UUID.randomUUID();
        var fromItemId = UUID.randomUUID();
        var toItemId = UUID.randomUUID();
        var relationshipId = UUID.randomUUID();
        var command = new CreatePkbRelationshipCommand(userId, fromItemId, toItemId, " Supports ", "correlation-rel");
        when(itemDao.existsByUserAndItemId(userId, fromItemId)).thenReturn(true);
        when(itemDao.existsByUserAndItemId(userId, toItemId)).thenReturn(true);
        when(relationshipDao.save(any())).thenAnswer(invocation -> {
            PkbRelationshipEntity relationship = invocation.getArgument(0);
            relationship.setRelationshipId(relationshipId);
            return relationship;
        });

        var savedRelationship = commandService.handle(command);

        assertThat(savedRelationship.getRelationshipType()).isEqualTo("supports");
        var eventCaptor = ArgumentCaptor.forClass(PkbDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(PkbDomainEventType.RELATIONSHIP_CREATED);
        assertThat(eventCaptor.getValue().relationshipId()).isEqualTo(relationshipId);
    }

    private static CreatePkbItemCommand waterIntakeCommand(UUID userId) {
        return new CreatePkbItemCommand(
                userId,
                "Nutrition_Intake",
                "Water",
                "Active",
                new LinkedHashMap<>(Map.of("amount", 350, "unit", "ml")),
                "HealthKit",
                "sample-1",
                null,
                null,
                null,
                null,
                java.util.List.of("Nutrition-Read"),
                java.util.List.of("Nutrition", "Health"),
                "User_Reported",
                null,
                new PkbProvenanceCommand("HealthKit_Sync", "System", "workflow-1", "sample-1", "healthkit-import"),
                "correlation-create"
        );
    }
}
