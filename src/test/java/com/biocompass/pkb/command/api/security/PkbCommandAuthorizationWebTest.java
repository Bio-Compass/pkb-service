package com.biocompass.pkb.command.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biocompass.pkb.authorization.PkbAuthorizationDecision;
import com.biocompass.pkb.authorization.PkbAuthorizationGateway;
import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import com.biocompass.pkb.authorization.PkbAuthorizationUnavailableException;
import com.biocompass.pkb.authorization.PkbWriteAuthorizationService;
import com.biocompass.pkb.command.api.controller.PkbItemCommandController;
import com.biocompass.pkb.command.api.service.PkbCommandIngressService;
import com.biocompass.pkb.command.PkbCommandValidator;
import com.biocompass.pkb.config.SecurityConfig;
import com.biocompass.pkb.kafka.PkbCommandSubmissionGateway;
import com.biocompass.pkb.kafka.PkbKafkaCommandContext;
import com.biocompass.pkb.kafka.PkbKafkaProperties;
import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
        controllers = PkbItemCommandController.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*MapperImpl"),
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.biocompass\\.pkb\\.query\\..*"
        )
)
@Import({
        SecurityConfig.class,
        BioCompassActorAuthenticationResolver.class,
        PkbWriteAuthorizationService.class,
        PkbCommandValidator.class,
        PkbCommandIngressService.class
})
class PkbCommandAuthorizationWebTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REQUEST_BODY = """
            {
              "entityType":"observation",
              "subtype":"note",
              "status":"active",
              "payload":{"text":"drink water"},
              "sourceType":"manual",
              "provenance":{"sourceKind":"manual"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpaqueTokenIntrospector opaqueTokenIntrospector;

    @MockitoBean
    private PkbAuthorizationGateway authorizationGateway;

    @MockitoBean
    private PkbCommandSubmissionGateway commandSubmissionGateway;

    @MockitoBean
    private PkbKafkaProperties kafkaProperties;

    @BeforeEach
    void configureBoundaries() {
        when(opaqueTokenIntrospector.introspect(anyString())).thenAnswer(invocation -> switch (
                invocation.getArgument(0, String.class)) {
            case "valid-user" -> principal(USER_ID);
            default -> throw new BadOpaqueTokenException("Inactive BioCompass access token");
        });
        when(kafkaProperties.commandIngressEnabled()).thenReturn(true);
        when(kafkaProperties.producerService()).thenReturn("pkb-service");
        when(authorizationGateway.decide(any())).thenAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            boolean ownerWrite = request.actor().userId().equals(request.resource().targetUserId());
            return decision(ownerWrite, request.resource().targetUserId());
        });
    }

    @Test
    void requiresBearerTokenBeforeAuthorizationOrSubmission() throws Exception {
        mockMvc.perform(commandRequest(USER_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authorizationGateway, commandSubmissionGateway);
    }

    @Test
    void carriesAuthenticatedActorAndAuDecisionReferenceToKafka() throws Exception {
        var commandId = UUID.randomUUID();

        mockMvc.perform(commandRequest(USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user")
                        .header("X-Command-Id", commandId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.commandId").value(commandId.toString()));

        var authorizationRequest = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(authorizationGateway).decide(authorizationRequest.capture());
        assertThat(authorizationRequest.getValue().producerService()).isEqualTo("pkb-service");
        assertThat(authorizationRequest.getValue().actor().userId()).isEqualTo(USER_ID);
        assertThat(authorizationRequest.getValue().resource().targetUserId()).isEqualTo(USER_ID);

        var context = ArgumentCaptor.forClass(PkbKafkaCommandContext.class);
        verify(commandSubmissionGateway).submit(any(), any(), context.capture());
        assertThat(context.getValue().actor().userId()).isEqualTo(USER_ID);
        assertThat(context.getValue().priorDecisionReference()).isEqualTo("decision-test");
    }

    @Test
    void failsClosedWhenAuDeniesTheWrite() throws Exception {
        mockMvc.perform(commandRequest(OTHER_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user")
                        .header("X-Command-Id", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("pkb_write_denied"));

        verify(commandSubmissionGateway, never()).submit(any(), any(), any());
    }

    @Test
    void failsClosedWhenAuIsUnavailable() throws Exception {
        doThrow(new PkbAuthorizationUnavailableException("BioCompass AU is unavailable", null))
                .when(authorizationGateway).decide(any());

        mockMvc.perform(commandRequest(USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user")
                        .header("X-Command-Id", UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pkb_authorization_unavailable"));

        verify(commandSubmissionGateway, never()).submit(any(), any(), any());
    }

    @Test
    void rejectsAnAuDecisionBoundToAnotherTarget() throws Exception {
        doReturn(decision(true, OTHER_USER_ID)).when(authorizationGateway).decide(any());

        mockMvc.perform(commandRequest(USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user")
                        .header("X-Command-Id", UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pkb_authorization_unavailable"));

        verify(commandSubmissionGateway, never()).submit(any(), any(), any());
    }

    private static PkbAuthorizationDecision decision(boolean allowed, UUID targetUserId) {
        return new PkbAuthorizationDecision(allowed, "decision-test", "write", targetUserId, Map.of());
    }

    private static MockHttpServletRequestBuilder commandRequest(UUID userId) {
        return post("/api/pkb/commands/items")
                .queryParam("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY);
    }

    private static DefaultOAuth2AuthenticatedPrincipal principal(UUID userId) {
        return new DefaultOAuth2AuthenticatedPrincipal(
                userId.toString(),
                Map.of(
                        "user_id", userId.toString(),
                        "email", "user@example.com",
                        "email_verified", true,
                        "is_staff", false,
                        "roles", Set.of("user")
                ),
                List.of(new SimpleGrantedAuthority("ROLE_user"))
        );
    }
}
