package com.biocompass.pkb.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbArtifactConsentBindingCommand;
import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.command.dto.PkbProvenanceCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = PkbWriteAuthorizationServiceTest.TestApplication.class)
class PkbWriteAuthorizationServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private PkbWriteAuthorizationService authorizationService;

    @MockitoBean
    private PkbAuthorizationGateway authorizationGateway;

    @BeforeEach
    void allowBoundDecision() {
        when(authorizationGateway.decide(any())).thenAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return decision(true, request.action(), request.resource().targetUserId(), Map.of());
        });
    }

    @Test
    void buildsCompleteItemAuthorizationInputAndReturnsDecisionReference() {
        var commandId = UUID.randomUUID();

        assertThat(authorizationService.authorize(commandId, command(), context()))
                .isEqualTo("decision-test");

        var request = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(authorizationGateway).decide(request.capture());
        assertThat(request.getValue().commandId()).isEqualTo(commandId);
        assertThat(request.getValue().action()).isEqualTo("write");
        assertThat(request.getValue().producerService()).isEqualTo("notification-service");
        assertThat(request.getValue().actor()).usingRecursiveComparison().isEqualTo(context().actor());
        assertThat(request.getValue().priorDecisionReference()).isEqualTo("decision-http");
        assertThat(request.getValue().resource().targetUserId()).isEqualTo(USER_ID);
        assertThat(request.getValue().resource().commandType()).isEqualTo("CreatePkbItemCommand");
        assertThat(request.getValue().resource().sourceType()).isEqualTo("manual");
        assertThat(request.getValue().resource().consentScope()).containsExactly("self-read");
        assertThat(request.getValue().resource().privacyScope()).containsExactly("health");
    }

    @Test
    void buildsSupersessionAuthorizationInput() {
        var supersededItemId = UUID.randomUUID();
        var replacement = new CreatePkbItemCommand(
                USER_ID,
                "observation",
                "note",
                "active",
                Map.of("text", "replacement"),
                "device",
                "note-2",
                null,
                null,
                null,
                null,
                List.of("care-write"),
                List.of("restricted"),
                null,
                supersededItemId,
                new PkbProvenanceCommand("device", null, null, null, null),
                "correlation-2");

        var resource = authorizeAndCapture(new SupersedePkbItemCommand(
                USER_ID,
                supersededItemId,
                replacement,
                "correlation-2"));

        assertThat(resource.commandType()).isEqualTo("SupersedePkbItemCommand");
        assertThat(resource.sourceType()).isEqualTo("device");
        assertThat(resource.consentScope()).containsExactly("care-write");
        assertThat(resource.privacyScope()).containsExactly("restricted");
        assertThat(resource.identifiers()).containsEntry("supersededItemId", supersededItemId);
    }

    @Test
    void buildsRelationshipAuthorizationInput() {
        var fromItemId = UUID.randomUUID();
        var toItemId = UUID.randomUUID();

        var resource = authorizeAndCapture(new CreatePkbRelationshipCommand(
                USER_ID,
                fromItemId,
                toItemId,
                "supports",
                "correlation-3"));

        assertThat(resource.commandType()).isEqualTo("CreatePkbRelationshipCommand");
        assertThat(resource.sourceType()).isNull();
        assertThat(resource.consentScope()).isEmpty();
        assertThat(resource.privacyScope()).isEmpty();
        assertThat(resource.identifiers())
                .containsEntry("fromItemId", fromItemId)
                .containsEntry("toItemId", toItemId);
    }

    @Test
    void buildsArtifactRegistrationAuthorizationInput() {
        var itemId = UUID.randomUUID();
        var consent = new PkbArtifactConsentBindingCommand(
                "consent-1",
                List.of("document-write"),
                null,
                "care",
                null,
                null);

        var resource = authorizeAndCapture(new RegisterPkbArtifactCommand(
                USER_ID,
                itemId,
                UUID.randomUUID(),
                "report.pdf",
                "application/pdf",
                42L,
                "a".repeat(64),
                new PkbProvenanceCommand("upload", "user", null, null, null),
                consent,
                false,
                "correlation-4"));

        assertThat(resource.commandType()).isEqualTo("RegisterPkbArtifactCommand");
        assertThat(resource.sourceType()).isEqualTo("upload");
        assertThat(resource.consentScope()).containsExactly("document-write");
        assertThat(resource.privacyScope()).isEmpty();
        assertThat(resource.identifiers()).containsEntry("pkbItemId", itemId);
    }

    @Test
    void buildsArtifactAssociationAuthorizationInput() {
        var artifactId = UUID.randomUUID();
        var itemId = UUID.randomUUID();

        var resource = authorizeAndCapture(new AssociatePkbArtifactCommand(
                USER_ID,
                artifactId,
                itemId,
                "correlation-5"));

        assertThat(resource.commandType()).isEqualTo("AssociatePkbArtifactCommand");
        assertThat(resource.sourceType()).isNull();
        assertThat(resource.consentScope()).isEmpty();
        assertThat(resource.privacyScope()).isEmpty();
        assertThat(resource.identifiers())
                .containsEntry("artifactId", artifactId)
                .containsEntry("pkbItemId", itemId);
    }

    @Test
    void rejectsAllowedDecisionWithUnsupportedObligations() {
        doReturn(decision(true, "write", USER_ID, Map.of("redact", true)))
                .when(authorizationGateway).decide(any());

        assertThatThrownBy(() -> authorizationService.authorize(UUID.randomUUID(), command(), context()))
                .isInstanceOf(PkbAuthorizationInvalidDecisionException.class)
                .hasMessage("BioCompass AU returned unsupported write obligations");
    }

    @Test
    void rejectsDecisionWhoseActionIsNotBoundToTheCommand() {
        doReturn(decision(true, "read", USER_ID, Map.of()))
                .when(authorizationGateway).decide(any());

        assertThatThrownBy(() -> authorizationService.authorize(UUID.randomUUID(), command(), context()))
                .isInstanceOf(PkbAuthorizationInvalidDecisionException.class)
                .hasMessage("BioCompass AU decision is not bound to this PKB write");
    }

    @Test
    void rejectsDecisionBoundToDifferentTargetUser() {
        doReturn(decision(true, "write", UUID.randomUUID(), Map.of()))
                .when(authorizationGateway).decide(any());

        assertThatThrownBy(() -> authorizationService.authorize(UUID.randomUUID(), command(), context()))
                .isInstanceOf(PkbAuthorizationInvalidDecisionException.class)
                .hasMessage("BioCompass AU decision is not bound to this PKB write");
    }

    private static PkbAuthorizationDecision decision(
            boolean allowed,
            String action,
            UUID targetUserId,
            Map<String, Object> obligations
    ) {
        return new PkbAuthorizationDecision(allowed, "decision-test", action, targetUserId, obligations);
    }

    private PkbAuthorizationRequest.Resource authorizeAndCapture(PkbCommand<?> command) {
        authorizationService.authorize(UUID.randomUUID(), command, context());
        var request = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(authorizationGateway).decide(request.capture());
        assertThat(request.getValue().resource().targetUserId()).isEqualTo(USER_ID);
        return request.getValue().resource();
    }

    private static PkbKafkaCommandContext context() {
        return new PkbKafkaCommandContext(
                "notification-service",
                new PkbAuthorizationRequest.Actor(
                        USER_ID.toString(),
                        USER_ID,
                        false,
                        Set.of("user"),
                        Set.of("pkb:write"),
                        "self"),
                "decision-http");
    }

    private static CreatePkbItemCommand command() {
        return new CreatePkbItemCommand(
                USER_ID,
                "observation",
                "note",
                "active",
                Map.of("text", "drink water"),
                "manual",
                "note-1",
                null,
                null,
                null,
                "en",
                List.of("self-read"),
                List.of("health"),
                "verified",
                null,
                new PkbProvenanceCommand("manual", "user", "workflow-1", "note-1", "direct"),
                "correlation-1");
    }

    @SpringBootConfiguration
    @Import(PkbWriteAuthorizationService.class)
    static class TestApplication {
    }
}
