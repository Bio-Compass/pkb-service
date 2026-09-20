package com.biocompass.pkb.command.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biocompass.pkb.command.PkbCommandValidationException;
import com.biocompass.pkb.command.PkbCommandValidator;
import com.biocompass.pkb.command.api.service.PkbCommandIngressService;
import com.biocompass.pkb.command.dto.AssociatePkbArtifactCommand;
import com.biocompass.pkb.command.dto.CreatePkbItemCommand;
import com.biocompass.pkb.command.dto.CreatePkbRelationshipCommand;
import com.biocompass.pkb.command.dto.PkbCommand;
import com.biocompass.pkb.command.dto.RegisterPkbArtifactCommand;
import com.biocompass.pkb.command.dto.SupersedePkbItemCommand;
import com.biocompass.pkb.kafka.PkbCommandSubmissionGateway;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import com.biocompass.pkb.kafka.PkbKafkaProperties;
import com.biocompass.pkb.kafka.PkbKafkaCommandPublicationException;
import com.biocompass.pkb.authorization.PkbWriteAuthorizationService;
import com.biocompass.pkb.security.BioCompassActor;
import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {
                PkbItemCommandController.class,
                PkbRelationshipCommandController.class,
                PkbArtifactCommandController.class
        },
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*CommandMapperImpl"),
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.biocompass\\.pkb\\.query\\..*"
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({PkbCommandIngressService.class, PkbCommandValidator.class})
class PkbCommandControllerWebTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VALID_ITEM_REQUEST = """
            {
              "entityType":"observation",
              "subtype":"note",
              "status":"active",
              "payload":{"text":"drink water"},
              "sourceType":"manual",
              "sourceId":"note-1",
              "language":"en",
              "consentScope":["self-read"],
              "privacyScope":["health"],
              "verificationStatus":"user_reported",
              "provenance":{"sourceKind":"manual","actorType":"user"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PkbCommandSubmissionGateway commandSubmissionGateway;

    @MockitoBean
    private PkbWriteAuthorizationService authorizationService;

    @MockitoBean
    private BioCompassActorAuthenticationResolver actorResolver;

    @MockitoBean
    private PkbKafkaProperties kafkaProperties;

    @BeforeEach
    void configureIngressContext() {
        when(actorResolver.resolve()).thenReturn(new BioCompassActor(USER_ID, Set.of("user")));
        when(kafkaProperties.commandIngressEnabled()).thenReturn(true);
        when(kafkaProperties.producerService()).thenReturn("pkb-service");
        when(authorizationService.authorize(any(), any(), any())).thenReturn("decision-http");
    }

    @Test
    void createsItem() throws Exception {
        var commandId = UUID.randomUUID();

        mockMvc.perform(post("/api/pkb/commands/items")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .header("X-Correlation-Id", "item-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.commandId").value(commandId.toString()))
                .andExpect(jsonPath("$.correlationId").value("item-correlation"));

        assertThat(capturedCommand(commandId)).isInstanceOfSatisfying(CreatePkbItemCommand.class, command -> {
            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.payload()).containsEntry("text", "drink water");
            assertThat(command.consentScope()).containsExactly("self-read");
            assertThat(command.privacyScope()).containsExactly("health");
            assertThat(command.correlationId()).isEqualTo("item-correlation");
        });
    }

    @Test
    void supersedesItem() throws Exception {
        var commandId = UUID.randomUUID();
        var itemId = UUID.randomUUID();

        mockMvc.perform(post("/api/pkb/commands/items/{itemId}/supersessions", itemId)
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "replacementItem": {
                                    "entityType":"observation",
                                    "subtype":"note",
                                    "status":"active",
                                    "payload":{"text":"updated note"},
                                    "sourceType":"manual",
                                    "provenance":{"sourceKind":"manual"}
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value(commandId.toString()));

        assertThat(capturedCommand(commandId)).isInstanceOfSatisfying(SupersedePkbItemCommand.class, command -> {
            assertThat(command.supersededItemId()).isEqualTo(itemId);
            assertThat(command.replacementItem().userId()).isEqualTo(USER_ID);
            assertThat(command.replacementItem().supersedes()).isEqualTo(itemId);
            assertThat(command.correlationId()).isEqualTo(commandId.toString());
        });
    }

    @Test
    void createsRelationship() throws Exception {
        var commandId = UUID.randomUUID();
        var fromItemId = UUID.randomUUID();
        var toItemId = UUID.randomUUID();

        mockMvc.perform(post("/api/pkb/commands/relationships")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .header("X-Correlation-Id", "relationship-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromItemId":"%s",
                                  "toItemId":"%s",
                                  "relationshipType":"supports"
                                }
                                """.formatted(fromItemId, toItemId)))
                .andExpect(status().isAccepted());

        assertThat(capturedCommand(commandId)).isEqualTo(new CreatePkbRelationshipCommand(
                USER_ID,
                fromItemId,
                toItemId,
                "supports",
                "relationship-correlation"));
    }

    @Test
    void registersArtifact() throws Exception {
        var commandId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var documentId = UUID.randomUUID();

        mockMvc.perform(post("/api/pkb/commands/artifacts")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .header("X-Correlation-Id", "artifact-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pkbItemId":"%s",
                                  "documentId":"%s",
                                  "objectName":"report.pdf",
                                  "contentType":"application/pdf",
                                  "sizeBytes":42,
                                  "sha256":"%s",
                                  "provenance":{"sourceKind":"upload"},
                                  "requestEnrichment":true
                                }
                                """.formatted(itemId, documentId, "a".repeat(64))))
                .andExpect(status().isAccepted());

        assertThat(capturedCommand(commandId)).isInstanceOfSatisfying(RegisterPkbArtifactCommand.class, command -> {
            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.pkbItemId()).isEqualTo(itemId);
            assertThat(command.documentId()).isEqualTo(documentId);
            assertThat(command.objectName()).isEqualTo("report.pdf");
            assertThat(command.requestEnrichment()).isTrue();
        });
    }

    @Test
    void associatesArtifact() throws Exception {
        var commandId = UUID.randomUUID();
        var artifactId = UUID.randomUUID();
        var itemId = UUID.randomUUID();

        mockMvc.perform(post("/api/pkb/commands/artifacts/{artifactId}/associations", artifactId)
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .header("X-Correlation-Id", "association-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pkbItemId":"%s"}
                                """.formatted(itemId)))
                .andExpect(status().isAccepted());

        assertThat(capturedCommand(commandId)).isEqualTo(new AssociatePkbArtifactCommand(
                USER_ID,
                artifactId,
                itemId,
                "association-correlation"));
    }

    @Test
    void returnsBadRequestForInvalidCommand() throws Exception {
        var commandId = UUID.randomUUID();
        doThrow(new PkbCommandValidationException(List.of("payload is required")))
                .when(commandSubmissionGateway).submit(eq(commandId), any(), any());

        mockMvc.perform(post("/api/pkb/commands/items")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("pkb_invalid_command"))
                .andExpect(jsonPath("$.message").value("payload is required"));
    }

    @Test
    void validatesMappedCommandBeforeCallingAuthorization() throws Exception {
        mockMvc.perform(post("/api/pkb/commands/artifacts")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":"11111111-2222-3333-4444-555555555555",
                                  "objectName":"report.pdf",
                                  "contentType":"application/pdf",
                                  "sizeBytes":42,
                                  "sha256":"%s",
                                  "requestEnrichment":false
                                }
                                """.formatted("a".repeat(64))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("pkb_invalid_command"))
                .andExpect(jsonPath("$.message").value(containsString("provenance is required")));

        verify(authorizationService, never()).authorize(any(), any(), any());
        verify(commandSubmissionGateway, never()).submit(any(), any(), any());
    }

    @Test
    void failsClosedWhenCommandIngressIsDisabled() throws Exception {
        when(kafkaProperties.commandIngressEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/pkb/commands/items")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pkb_command_not_accepted"))
                .andExpect(jsonPath("$.message").value("PKB command ingress is disabled"));

        verify(actorResolver, never()).resolve();
        verify(authorizationService, never()).authorize(any(), any(), any());
        verify(commandSubmissionGateway, never()).submit(any(), any(), any());
    }

    @Test
    void returnsServiceUnavailableWhenKafkaRejectsCommand() throws Exception {
        var commandId = UUID.randomUUID();
        doThrow(new PkbKafkaCommandPublicationException("Kafka did not acknowledge the PKB command", null))
                .when(commandSubmissionGateway).submit(eq(commandId), any(), any());

        mockMvc.perform(post("/api/pkb/commands/items")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pkb_command_not_accepted"))
                .andExpect(jsonPath("$.message").value("Kafka did not acknowledge the PKB command"));
    }

    @Test
    void rejectsMissingCommandId() throws Exception {
        mockMvc.perform(post("/api/pkb/commands/items")
                        .queryParam("userId", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_REQUEST))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedCommandId() throws Exception {
        mockMvc.perform(post("/api/pkb/commands/items")
                        .queryParam("userId", USER_ID.toString())
                        .header("X-Command-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ITEM_REQUEST))
                .andExpect(status().isBadRequest());
    }

    private PkbCommand<?> capturedCommand(UUID commandId) {
        @SuppressWarnings("rawtypes")
        var command = ArgumentCaptor.forClass(PkbCommand.class);
        verify(commandSubmissionGateway).submit(eq(commandId), command.capture(), any(PkbKafkaCommandContext.class));
        return command.getValue();
    }
}
