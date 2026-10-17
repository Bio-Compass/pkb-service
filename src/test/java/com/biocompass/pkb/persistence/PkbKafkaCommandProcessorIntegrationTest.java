package com.biocompass.pkb.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.biocompass.pkb.authorization.PkbAuthorizationDecision;
import com.biocompass.pkb.authorization.PkbAuthorizationDeniedException;
import com.biocompass.pkb.authorization.PkbAuthorizationGateway;
import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import com.biocompass.pkb.authorization.PkbAuthorizationUnavailableException;
import com.biocompass.pkb.command.PkbCommandValidationException;
import com.biocompass.pkb.command.PkbCommandNotFoundException;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.kafka.PkbCommandIdReuseException;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import com.biocompass.pkb.kafka.PkbKafkaCommandMessage;
import com.biocompass.pkb.kafka.PkbKafkaCommandProcessor;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.repository.PkbProcessedCommandRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class PkbKafkaCommandProcessorIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PkbKafkaCommandProcessor commandProcessor;

    @Autowired
    private PkbItemDao itemDao;

    @Autowired
    private PkbProcessedCommandRepository processedCommandRepository;

    @MockitoBean
    private PkbAuthorizationGateway authorizationGateway;

    @BeforeEach
    void authorizeWrites() {
        when(authorizationGateway.decide(any())).thenAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return new PkbAuthorizationDecision(
                    true,
                    "decision-consumer-" + request.commandId(),
                    request.action(),
                    request.resource().targetUserId(),
                    Map.of());
        });
    }

    @Test
    void writesCanonicalItemOnceAndAuditsAnExactRedelivery() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        var message = message(commandId, createItemCommand(userId, "drink water"));

        boolean firstProcessing = commandProcessor.process(message);
        boolean duplicateProcessing = commandProcessor.process(message);

        assertThat(firstProcessing).isTrue();
        assertThat(duplicateProcessing).isFalse();
        assertThat(itemDao.findAllByUser(userId)).singleElement()
                .extracting(item -> item.getPayload().get("text"))
                .isEqualTo("drink water");

        var audit = processedCommandRepository.findById(commandId).orElseThrow();
        assertThat(audit.getCommandType()).isEqualTo(CreatePkbItemCommand.class.getSimpleName());
        assertThat(audit.getPayloadHash()).hasSize(64);
        assertThat(audit.getProducerService()).isEqualTo("notification-service");
        assertThat(audit.getActorUserId()).isEqualTo(userId);
        assertThat(audit.getPriorDecisionReference()).isEqualTo("decision-http");
        assertThat(audit.getAppliedDecisionReference()).isEqualTo("decision-consumer-" + commandId);
        assertThat(audit.getDeliveryCount()).isEqualTo(2);
        verify(authorizationGateway, times(1)).decide(any());
    }

    @Test
    void rejectsSameIdForDifferentUser() {
        var commandId = UUID.randomUUID();
        commandProcessor.process(message(commandId, createItemCommand(UUID.randomUUID(), "water")));

        assertThatThrownBy(() -> commandProcessor.process(
                message(commandId, createItemCommand(UUID.randomUUID(), "water"))))
                .isInstanceOf(PkbCommandIdReuseException.class)
                .hasMessageContaining("different user, type, or payload");
    }

    @Test
    void rejectsSameIdForDifferentCommandType() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        commandProcessor.process(message(commandId, createItemCommand(userId, "water")));
        var relationship = new CreatePkbRelationshipCommand(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "supports",
                "correlation-42");

        assertThatThrownBy(() -> commandProcessor.process(message(commandId, relationship)))
                .isInstanceOf(PkbCommandIdReuseException.class)
                .hasMessageContaining("different user, type, or payload");
    }

    @Test
    void rejectsSameIdForDifferentPayload() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        commandProcessor.process(message(commandId, createItemCommand(userId, "water")));

        assertThatThrownBy(() -> commandProcessor.process(
                message(commandId, createItemCommand(userId, "electrolytes"))))
                .isInstanceOf(PkbCommandIdReuseException.class)
                .hasMessageContaining("different user, type, or payload");
    }

    @Test
    void failsClosedBeforePersistenceWhenConsumerAuthorizationIsDenied() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        doReturn(new PkbAuthorizationDecision(
                false,
                "decision-denied",
                "write",
                userId,
                Map.of())).when(authorizationGateway).decide(any());

        assertThatThrownBy(() -> commandProcessor.process(
                message(commandId, createItemCommand(userId, "water"))))
                .isInstanceOf(PkbAuthorizationDeniedException.class);

        assertThat(processedCommandRepository.existsById(commandId)).isFalse();
        assertThat(itemDao.findAllByUser(userId)).isEmpty();
    }

    @Test
    void failsClosedBeforePersistenceWhenConsumerAuthorizationIsUnavailable() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        doThrow(new PkbAuthorizationUnavailableException("AU unavailable", null))
                .when(authorizationGateway).decide(any());

        assertThatThrownBy(() -> commandProcessor.process(
                message(commandId, createItemCommand(userId, "water"))))
                .isInstanceOf(PkbAuthorizationUnavailableException.class);

        assertThat(processedCommandRepository.existsById(commandId)).isFalse();
        assertThat(itemDao.findAllByUser(userId)).isEmpty();
    }

    @Test
    void rollsBackProcessedMarkerWhenCommandHandlingFails() {
        var userId = UUID.randomUUID();
        var commandId = UUID.randomUUID();
        var relationship = new CreatePkbRelationshipCommand(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "supports",
                "correlation-42");

        assertThatThrownBy(() -> commandProcessor.process(message(commandId, relationship)))
                .isInstanceOf(PkbCommandNotFoundException.class);
        assertThat(processedCommandRepository.existsById(commandId)).isFalse();
    }

    @Test
    void rejectsInvalidEnvelopeBeforeAuthorizationOrPersistence() {
        var commandId = UUID.randomUUID();
        var invalidCommand = new CreatePkbItemCommand(
                UUID.randomUUID(),
                "observation",
                "note",
                "active",
                null,
                "manual",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new PkbProvenanceCommand("manual", null, null, null, null),
                null);

        assertThatThrownBy(() -> commandProcessor.process(message(commandId, invalidCommand)))
                .isInstanceOf(PkbCommandValidationException.class)
                .hasMessageContaining("payload is required");
        assertThat(processedCommandRepository.existsById(commandId)).isFalse();
        verifyNoInteractions(authorizationGateway);
    }

    private static PkbKafkaCommandMessage message(UUID commandId, PkbCommand<?> command) {
        return new PkbKafkaCommandMessage(commandId, Instant.now(), context(command.userId()), command);
    }

    private static PkbKafkaCommandContext context(UUID userId) {
        return new PkbKafkaCommandContext(
                "notification-service",
                new PkbAuthorizationRequest.Actor(
                        userId.toString(),
                        userId,
                        false,
                        Set.of("user"),
                        Set.of("pkb:write"),
                        "self"),
                "decision-http");
    }

    private static CreatePkbItemCommand createItemCommand(UUID userId, String text) {
        return new CreatePkbItemCommand(
                userId,
                "observation",
                "note",
                "active",
                Map.of("text", text),
                "manual",
                "note-" + UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new PkbProvenanceCommand("manual", "user", null, null, null),
                "correlation-42");
    }
}
