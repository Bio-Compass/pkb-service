package com.biocompass.pkb.records;

import com.biocompass.pkb.authorization.PkbAuthorizationDecision;
import com.biocompass.pkb.authorization.PkbAuthorizationGateway;
import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import com.biocompass.pkb.authorization.PkbAuthorizationUnavailableException;
import com.biocompass.pkb.config.SecurityConfig;
import com.biocompass.pkb.records.persistence.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PkbRecordController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.biocompass\\.pkb\\.query\\..*"))
@Import({PkbRecordSecurityConfiguration.class, SecurityConfig.class, PkbRecordAuthorityClient.class,
        PkbRecordAuthorization.class, PkbRecordApiTest.ClientConfiguration.class})
class PkbRecordApiTest {
    private static final String BASE = "/api/v1/internal/items";
    private static final String PRODUCER = "producer-service";
    private static final String WORKLOAD_TOKEN = "fixture-workload-unique";
    private static final String PKB_BAS_TOKEN = "fixture-pkb-to-bas-unique";
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ITEM = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID KEY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant START = Instant.parse("2026-10-24T09:00:00Z");
    private static final AtomicReference<String> BAS_BODY = new AtomicReference<>();
    private static final AtomicInteger BAS_STATUS = new AtomicInteger(200);
    private static final AtomicInteger BAS_CALLS = new AtomicInteger();
    private static final AtomicReference<String> FORWARDED_BODY = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_SERVICE = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_LENGTH = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_ENCODING = new AtomicReference<>();
    private static final HttpServer BAS = startBas();

    @Autowired private MockMvc mvc;
    @MockitoBean private PkbSourceRecordStore store;
    @MockitoBean private PkbAuthorizationGateway gateway;
    @MockitoBean private OpaqueTokenIntrospector legacyIntrospector;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("biocompass.pkb.records.service-tokens.producer-service", () -> WORKLOAD_TOKEN);
        registry.add("biocompass.pkb.auth.legacy.service-tokens.producer-service", () -> "fixture-legacy-plane-only");
        registry.add("biocompass.pkb.records.authority-service-token", () -> PKB_BAS_TOKEN);
        registry.add("biocompass.pkb.records.authority-url", () -> "http://127.0.0.1:" + BAS.getAddress().getPort()
                + "/api/v1/internal/auth/backend-authority/verify/");
    }

    @AfterAll static void stopBas() { BAS.stop(0); }

    @BeforeEach void allowVerifiedAuthorityAndPolicy() {
        BAS_STATUS.set(200); BAS_CALLS.set(0); FORWARDED_BODY.set(null);
        FORWARDED_LENGTH.set(null); FORWARDED_ENCODING.set(null);
        BAS_BODY.set(activeResponse(PRODUCER, "[\"hydration:read\",\"hydration:summary:read\",\"hydration:create\",\"hydration:delete\"]"));
        doAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return decision(true, request.action());
        }).when(gateway).decide(any());
        when(store.create(any())).thenAnswer(invocation -> new PkbSourceRecordWriteResult("created", fromCreate(invocation.getArgument(0))));
        when(store.find(USER, PRODUCER, ITEM)).thenReturn(Optional.of(item()));
        when(store.findIncludingDeleted(USER, PRODUCER, ITEM)).thenReturn(Optional.of(item()));
        when(store.aggregationItems(any(), anyString(), anyString(), anyString(), anyString(), anyList(),
                nullable(UUID.class), anyInt()))
                .thenReturn(List.of(new PkbSourceRecordAccess(ITEM, "device", List.of("health"), List.of("self"))));
        when(store.totals(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(List.of(new BigDecimal("4294967294")));
        when(store.recentQuantities(any(), anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(new BigDecimal("250")));
        when(store.history(any(), anyString(), anyString(), anyString(), any(), any(), anyInt(), nullable(PkbSourceRecordCursor.class)))
                .thenReturn(List.of(item()));
    }

    @Test void missingWorkloadAndUserCredentialsRejectBeforeBodyParsingOrStorage() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthorized"));
        assertThat(BAS_CALLS.get()).isZero();
        verifyNoInteractions(store, gateway, legacyIntrospector);
    }

    @Test void workloadCredentialWithoutUserAuthorityIsInsufficient() throws Exception {
        mvc.perform(post(BASE).header("Authorization", "Bearer " + WORKLOAD_TOKEN).header("X-BioCompass-Service", PRODUCER)
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isUnauthorized());
        assertThat(BAS_CALLS.get()).isZero();
        verifyNoInteractions(store, gateway);
    }

    @Test void userAuthorityWithoutWorkloadAuthenticationIsInsufficient() throws Exception {
        mvc.perform(post(BASE).header("X-BioCompass-User-Token", "mobile-user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isUnauthorized());
        assertThat(BAS_CALLS.get()).isZero();
        verifyNoInteractions(store, gateway);
    }

    @Test void legacyCredentialDoesNotAuthenticateOnRecordPlane() throws Exception {
        mvc.perform(post(BASE).header("Authorization", "Bearer fixture-legacy-plane-only")
                        .header("X-BioCompass-Service", PRODUCER).header("X-BioCompass-User-Token", "user-token"))
                .andExpect(status().isUnauthorized());
        assertThat(BAS_CALLS.get()).isZero();
        verifyNoInteractions(store, gateway);
    }

    @Test void wrongWorkloadOrSpoofedProducerNeverReachesBas() throws Exception {
        mvc.perform(post(BASE).header("Authorization", "Bearer wrong-token").header("X-BioCompass-Service", PRODUCER)
                        .header("X-BioCompass-User-Token", "user-token").contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).header("Authorization", "Bearer " + WORKLOAD_TOKEN).header("X-BioCompass-Service", "spoofed-producer")
                        .header("X-BioCompass-User-Token", "user-token").contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isUnauthorized());
        assertThat(BAS_CALLS.get()).isZero();
        verifyNoInteractions(store, gateway);
    }

    @ParameterizedTest @ValueSource(strings = {"mobile-user-token", "browser-user-token", "telegram-user-token", "other-api-token"})
    void everyClientChannelUsesSameVerifiedOwnerProducerAndAuContract(String userToken) throws Exception {
        mvc.perform(auth(post(BASE), userToken).contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.outcome").value("created"))
                .andExpect(jsonPath("$.item.user_id").value(USER.toString()))
                .andExpect(jsonPath("$.item.source_system").value(PRODUCER))
                .andExpect(jsonPath("$.item.quantity.value").value("250.123456789012"));
        assertThat(FORWARDED_BODY.get()).contains("\"token\":\"" + userToken + "\"", "\"resource_service\":\"" + PRODUCER + "\"");
        assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer " + PKB_BAS_TOKEN);
        assertThat(FORWARDED_SERVICE.get()).isEqualTo("pkb-service");
        var capture = ArgumentCaptor.forClass(PkbSourceRecordCreate.class);
        verify(store).create(capture.capture());
        assertThat(capture.getValue().userId()).isEqualTo(USER);
        assertThat(capture.getValue().producer()).isEqualTo(PRODUCER);
        assertThat(capture.getValue().workflowId()).isEqualTo("fixture-au-decision");
        var policy = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway).decide(policy.capture());
        assertThat(policy.getValue().actor().actorId()).isEqualTo("verified-actor");
        assertThat(policy.getValue().actor().staff()).isFalse();
        verifyNoInteractions(legacyIntrospector);
    }

    @Test void basWsgiReceivesLengthDelimitedJson() throws Exception {
        mvc.perform(auth(get(BASE + "/" + ITEM))).andExpect(status().isOk());
        assertThat(FORWARDED_LENGTH.get())
                .isEqualTo(String.valueOf(FORWARDED_BODY.get().getBytes(StandardCharsets.UTF_8).length));
        assertThat(FORWARDED_ENCODING.get()).isNull();
    }

    @Test void inactiveAuthorityFailsClosedWithoutPolicyOrStorage() throws Exception {
        BAS_BODY.set("{\"active\":false}");
        mvc.perform(auth(get(BASE + "/" + ITEM))).andExpect(status().isUnauthorized());
        verifyNoInteractions(store, gateway);
    }

    @Test void authorityAudienceMismatchIsUnavailableWithoutEchoingUpstream() throws Exception {
        BAS_BODY.set(activeResponse("different-service", "[\"hydration:read\"]"));
        mvc.perform(auth(get(BASE + "/" + ITEM))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("unavailable"))
                .andExpect(content().string(not(containsString(PKB_BAS_TOKEN))));
        verifyNoInteractions(store, gateway);
    }

    @Test void basFailureIsSafeUnavailable() throws Exception {
        BAS_STATUS.set(500); BAS_BODY.set("{\"detail\":\"internal token " + PKB_BAS_TOKEN + "\"}");
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString(PKB_BAS_TOKEN))));
        verifyNoInteractions(store, gateway);
    }

    @ParameterizedTest @ValueSource(strings = {"actor", "permissions", "purpose", "user", "active", "malformed"})
    void malformedOrIncompleteBasAuthorityFailsClosed(String field) throws Exception {
        var response = activeResponse(PRODUCER, "[\"hydration:read\"]");
        response = switch (field) {
            case "actor" -> response.replace("verified-actor", " ");
            case "permissions" -> response.replace("[\"hydration:read\"]", "[]");
            case "purpose" -> response.replace("\"self\"", "\"staff\"");
            case "user" -> response.replace(USER.toString(), "not-a-uuid");
            case "active" -> response.replace("\"active\":true", "\"active\":null");
            default -> "not-json";
        };
        BAS_BODY.set(response);
        mvc.perform(auth(get(BASE + "/" + ITEM))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("unavailable"));
        verifyNoInteractions(store, gateway);
    }

    @Test void auUnavailableIsSafeAndPreventsWrite() throws Exception {
        doThrow(new PkbAuthorizationUnavailableException("upstream secret " + PKB_BAS_TOKEN)).when(gateway).decide(any());
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("unavailable"))
                .andExpect(content().string(not(containsString(PKB_BAS_TOKEN))));
        verifyNoInteractions(store);
    }

    @Test void databaseUnavailableDoesNotExposeConnectionDetails() throws Exception {
        doThrow(new DataAccessResourceFailureException("jdbc credential " + WORKLOAD_TOKEN)).when(store).create(any());
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("unavailable"))
                .andExpect(content().string(not(containsString(WORKLOAD_TOKEN))));
    }

    @Test void callerSuppliedOwnerInBodyIsRejected() throws Exception {
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                        .content(validCreate().replaceFirst("\\{", "{\"user_id\":\"" + UUID.randomUUID() + "\",")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("invalid_request"));
        verifyNoInteractions(store, gateway);
    }

    @Test void callerSuppliedOwnerInQueryIsRejectedBeforeStorage() throws Exception {
        mvc.perform(auth(get(BASE + "/" + ITEM)).queryParam("user_id", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(store, gateway);
    }

    @ParameterizedTest @ValueSource(strings = {"idempotency_conflict", "key_retired", "source_conflict"})
    void stableConflictCodesDoNotExposeRequestSecrets(String code) throws Exception {
        doThrow(new PkbSourceRecordConflict(code)).when(store).create(any());
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code))
                .andExpect(content().string(not(containsString(WORKLOAD_TOKEN))))
                .andExpect(content().string(not(containsString("mobile-user-token"))));
    }

    @Test void replayReturnsOriginalCanonicalItemAndChecksItsActualGovernance() throws Exception {
        doReturn(new PkbSourceRecordWriteResult("replayed", item())).when(store).create(any());
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(validCreate()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("replayed"))
                .andExpect(jsonPath("$.item.item_id").value(ITEM.toString()))
                .andExpect(jsonPath("$.item.observed_at").value(START.toString()));
        var capture = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway, times(2)).decide(capture.capture());
        assertThat(capture.getAllValues().getLast().resource().sourceType()).isEqualTo("device");
        assertThat(capture.getAllValues().getLast().resource().consentScope()).containsExactly("self");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "NaN", "0.0000000000001"})
    void invalidQuantityNeverReachesPolicyOrStorage(String value) throws Exception {
        mvc.perform(auth(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                        .content(validCreate().replace("250.123456789012", value)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("invalid_request"));
        verifyNoInteractions(store, gateway);
    }

    @Test void invalidWindowNeverReachesPolicyOrStorage() throws Exception {
        mvc.perform(auth(post(BASE + "/quantity-totals")).contentType(MediaType.APPLICATION_JSON)
                        .content(totals().replace("2026-10-25T00:00:00Z", "2026-10-24T00:00:00Z")))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(store, gateway);
    }

    @Test void malformedCursorNeverReachesHistoryStorage() throws Exception {
        mvc.perform(auth(history()).queryParam("cursor", "invalid-cursor"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("invalid_request"));
        verify(store, never()).history(any(), anyString(), anyString(), anyString(), any(), any(), anyInt(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "501"})
    void historyPageLimitIsBoundedBeforeStorage(String limit) throws Exception {
        mvc.perform(auth(history()).queryParam("limit", limit)).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(store, gateway);
    }

    @Test void unsupportedRecentQuantityUnitIsRejectedBeforeStorage() throws Exception {
        mvc.perform(auth(get(BASE + "/recent-quantities").queryParam("entity_type", "nutrition_intake")
                        .queryParam("subtype", "water").queryParam("unit", "L")))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(store, gateway);
    }

    @Test void summaryOnlyPermissionDoesNotGrantRawReadsOrAggregates() throws Exception {
        BAS_BODY.set(activeResponse(PRODUCER, "[\"hydration:summary:read\"]"));
        enforceReadCapability();
        mvc.perform(auth(get(BASE + "/" + ITEM))).andExpect(status().isForbidden());
        mvc.perform(auth(post(BASE + "/quantity-totals")).contentType(MediaType.APPLICATION_JSON).content(totals()))
                .andExpect(status().isForbidden());
        mvc.perform(auth(recentRequest())).andExpect(status().isForbidden());
        verify(store).find(USER, PRODUCER, ITEM);
        verifyNoMoreInteractions(store);
    }

    @Test void intakeReadPermissionGrantsRawReadsAndItemAuthorizedAggregates() throws Exception {
        BAS_BODY.set(activeResponse(PRODUCER, "[\"hydration:intake:read\"]"));
        enforceReadCapability();
        mvc.perform(auth(get(BASE + "/" + ITEM))).andExpect(status().isOk());
        mvc.perform(auth(post(BASE + "/quantity-totals")).contentType(MediaType.APPLICATION_JSON).content(totals()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals[0]").value("4294967294"));
        mvc.perform(auth(recentRequest())).andExpect(status().isOk()).andExpect(jsonPath("$.values[0]").value("250"));
        var capture = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway, times(5)).decide(capture.capture());
        assertThat(capture.getAllValues()).extracting(PkbAuthorizationRequest::action)
                .containsExactly("read", "aggregate", "aggregate", "aggregate", "aggregate");
        assertThat(capture.getAllValues()).allSatisfy(request ->
                assertThat(request.actor().scopes()).containsExactly("hydration:intake:read"));
        assertThat(capture.getAllValues().stream().filter(request -> request.action().equals("aggregate")
                && !request.resource().identifiers().isEmpty())).hasSize(2).allSatisfy(request ->
                assertThat(request.resource().identifiers()).containsEntry("item_id", ITEM));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void itemSpecificDenialOnLaterPageRejectsEntireAggregate(boolean recent) throws Exception {
        var firstPage = java.util.stream.LongStream.rangeClosed(1, 500)
                .mapToObj(i -> new PkbSourceRecordAccess(new UUID(0, i), "device", List.of("health"), List.of("self"))).toList();
        var deniedId = new UUID(0, 501);
        doReturn(firstPage).when(store).aggregationItems(any(), anyString(), anyString(), anyString(), anyString(),
                anyList(), isNull(), eq(500));
        doReturn(List.of(new PkbSourceRecordAccess(deniedId, "device", List.of("health"), List.of("self"))))
                .when(store).aggregationItems(any(), anyString(), anyString(), anyString(), anyString(), anyList(),
                        eq(firstPage.getLast().itemId()), eq(500));
        doAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return decision(!deniedId.equals(request.resource().identifiers().get("item_id")), request.action());
        }).when(gateway).decide(any());
        var request = recent ? recentRequest() : post(BASE + "/quantity-totals")
                .contentType(MediaType.APPLICATION_JSON).content(totals());
        mvc.perform(auth(request)).andExpect(status().isForbidden()).andExpect(jsonPath("$.totals").doesNotExist())
                .andExpect(jsonPath("$.values").doesNotExist());
        verify(store, never()).totals(any(), anyString(), anyString(), anyString(), anyString(), anyList());
        verify(store, never()).recentQuantities(any(), anyString(), anyString(), anyString(), anyString(), anyInt());
        var capture = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway, times(502)).decide(capture.capture());
        assertThat(capture.getAllValues().getLast().resource().identifiers()).containsEntry("item_id", deniedId);
    }

    @Test void aggregationFollowsEveryPageBeforeExecutingTheQuantityQuery() throws Exception {
        var firstPage = java.util.stream.LongStream.rangeClosed(1, 500)
                .mapToObj(i -> new PkbSourceRecordAccess(new UUID(0, i), "device", List.of("health"), List.of())).toList();
        var last = new PkbSourceRecordAccess(new UUID(0, 501), "device", List.of("health"), List.of());
        doReturn(firstPage).when(store).aggregationItems(any(), anyString(), anyString(), anyString(), anyString(),
                anyList(), isNull(), eq(500));
        doReturn(List.of(last)).when(store).aggregationItems(any(), anyString(), anyString(), anyString(), anyString(),
                anyList(), eq(firstPage.getLast().itemId()), eq(500));
        mvc.perform(auth(post(BASE + "/quantity-totals")).contentType(MediaType.APPLICATION_JSON).content(totals()))
                .andExpect(status().isOk());
        var order = inOrder(gateway, store);
        order.verify(gateway).decide(argThat(request -> request.resource().identifiers().isEmpty()));
        order.verify(store).aggregationItems(eq(USER), eq(PRODUCER), eq("nutrition_intake"), eq("water"), eq("mL"),
                anyList(), isNull(), eq(500));
        order.verify(gateway, times(500)).decide(argThat(request -> request.resource().identifiers().size() == 1));
        order.verify(store).aggregationItems(eq(USER), eq(PRODUCER), eq("nutrition_intake"), eq("water"), eq("mL"),
                anyList(), eq(firstPage.getLast().itemId()), eq(500));
        order.verify(gateway).decide(argThat(request -> last.itemId().equals(request.resource().identifiers().get("item_id"))));
        order.verify(store).totals(any(), anyString(), anyString(), anyString(), anyString(), anyList());
    }

    @Test void itemDecisionFailurePreventsAggregateCalculation() throws Exception {
        doAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            if (!request.resource().identifiers().isEmpty()) throw new PkbAuthorizationUnavailableException("private upstream");
            return decision(true, request.action());
        }).when(gateway).decide(any());
        mvc.perform(auth(recentRequest())).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("private upstream"))));
        verify(store, never()).recentQuantities(any(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    private void enforceReadCapability() {
        doAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return decision(request.actor().scopes().contains("hydration:intake:read"), request.action());
        }).when(gateway).decide(any());
    }

    private static MockHttpServletRequestBuilder recentRequest() {
        return get(BASE + "/recent-quantities").queryParam("entity_type", "nutrition_intake")
                .queryParam("subtype", "water").queryParam("unit", "mL");
    }

    @Test void historyFollowsKeysetCursorAndAuthorizesActualReturnedRows() throws Exception {
        var second = new PkbSourceRecord(UUID.randomUUID(), USER, PRODUCER, UUID.randomUUID().toString(), UUID.randomUUID(),
                "a".repeat(64), "nutrition_intake", "water", BigDecimal.ONE, "mL", START.plusSeconds(1), START.plusSeconds(1),
                "self_reported", List.of("restricted"), List.of("explicit"), null);
        when(store.history(eq(USER), eq(PRODUCER), anyString(), anyString(), any(), any(), eq(2), isNull()))
                .thenReturn(List.of(item(), second));
        var response = mvc.perform(auth(history()).queryParam("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(second.itemId().toString());
        var expectedCursor = PkbRecordController.encodeCursor(item());
        mvc.perform(auth(history()).queryParam("cursor", expectedCursor))
                .andExpect(status().isOk());
        verify(store).history(USER, PRODUCER, "nutrition_intake", "water", Instant.parse("2026-10-24T00:00:00Z"),
                Instant.parse("2026-10-25T00:00:00Z"), 501, PkbRecordController.decodeCursor(expectedCursor));
    }

    @Test void identicalGovernanceDoesNotBypassAnItemSpecificDenial() throws Exception {
        var first = item();
        var deniedId = UUID.randomUUID();
        var denied = new PkbSourceRecord(deniedId, first.userId(), first.sourceSystem(), deniedId.toString(),
                UUID.randomUUID(), first.fingerprint(), first.entityType(), first.subtype(), first.value(),
                first.unit(), first.observedAt().plusSeconds(1), first.recordedAt(), first.sourceType(),
                first.privacyScope(), first.consentScope(), null);
        doReturn(List.of(first, denied)).when(store).history(any(), anyString(), anyString(), anyString(),
                any(), any(), anyInt(), nullable(PkbSourceRecordCursor.class));
        doAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return decision(!deniedId.equals(request.resource().identifiers().get("item_id")), request.action());
        }).when(gateway).decide(any());
        mvc.perform(auth(history())).andExpect(status().isForbidden()).andExpect(jsonPath("$.items").doesNotExist());
        var requests = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway, times(3)).decide(requests.capture());
        assertThat(requests.getAllValues()).extracting(request -> request.resource().identifiers().get("item_id"))
                .contains(ITEM, deniedId);
    }

    @Test void emptyTotalsDoNotInspectUnrelatedRecords() throws Exception {
        mvc.perform(auth(post(BASE + "/quantity-totals")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entity_type\":\"nutrition_intake\",\"subtype\":\"water\",\"unit\":\"mL\",\"windows\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.length()").value(0));
        verifyNoInteractions(store);
        verify(gateway).decide(argThat(request -> request.action().equals("aggregate")));
    }

    @Test void absentDeleteNeverTargetsAConcurrentCreate() throws Exception {
        doReturn(Optional.empty()).when(store).findIncludingDeleted(USER, PRODUCER, ITEM);
        doAnswer(invocation -> {
            // A new row becomes visible while the remote AU call is in flight.
            doReturn(Optional.of(item())).when(store).findIncludingDeleted(USER, PRODUCER, ITEM);
            return decision(true, "delete");
        }).when(gateway).decide(any());
        mvc.perform(auth(delete(BASE + "/" + ITEM))).andExpect(status().isNoContent());
        verify(store, never()).delete(any(), anyString(), any());
    }

    @Test void deletionRemainsNonEnumeratingForMissingOwnedRecord() throws Exception {
        when(store.findIncludingDeleted(USER, PRODUCER, ITEM)).thenReturn(Optional.empty());
        mvc.perform(auth(delete(BASE + "/" + ITEM))).andExpect(status().isNoContent());
        verify(store, never()).delete(any(), anyString(), any());
        verify(gateway).decide(argThat(request -> request.action().equals("delete") && request.resource().targetUserId().equals(USER)));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) { return auth(request, "mobile-user-token"); }
    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + WORKLOAD_TOKEN).header("X-BioCompass-Service", PRODUCER)
                .header("X-BioCompass-User-Token", token);
    }
    private static MockHttpServletRequestBuilder history() {
        return get(BASE).queryParam("entity_type", "nutrition_intake").queryParam("subtype", "water")
                .queryParam("observed_from", "2026-10-24T00:00:00Z").queryParam("observed_before", "2026-10-25T00:00:00Z");
    }
    private static PkbAuthorizationDecision decision(boolean allowed, String action) {
        return new PkbAuthorizationDecision(allowed, "fixture-au-decision", action, USER, Map.of());
    }
    private static String activeResponse(String resource, String permissions) {
        return "{\"active\":true,\"user_id\":\"" + USER + "\",\"actor_id\":\"verified-actor\",\"resource_service\":\""
                + resource + "\",\"permissions\":" + permissions + ",\"purpose_of_use\":\"self\"}";
    }
    private static String validCreate() {
        return """
                {"item_id":"%s","source_record_id":"%s","idempotency_key":"%s","fingerprint":"%s",
                 "entity_type":"nutrition_intake","subtype":"water","quantity":{"value":"250.123456789012","unit":"mL"},
                 "observed_at":"2026-10-24T09:00:00Z","recorded_at":"2026-10-24T09:00:01Z",
                 "source_type":"self_reported","privacy_scope":["nutrition","health"],"consent_scope":[]}
                """.formatted(ITEM, ITEM, KEY, "a".repeat(64));
    }
    private static String totals() {
        return """
                {"entity_type":"nutrition_intake","subtype":"water","unit":"mL",
                 "windows":[{"from":"2026-10-24T00:00:00Z","before":"2026-10-25T00:00:00Z"}]}
                """;
    }
    private static PkbSourceRecord item() {
        return new PkbSourceRecord(ITEM, USER, PRODUCER, ITEM.toString(), KEY, "a".repeat(64), "nutrition_intake", "water",
                new BigDecimal("250"), "mL", START, START.plusSeconds(1), "device", List.of("health"), List.of("self"), null);
    }
    private static PkbSourceRecord fromCreate(PkbSourceRecordCreate c) {
        return new PkbSourceRecord(c.itemId(), c.userId(), c.producer(), c.sourceRecordId(), c.idempotencyKey(), c.fingerprint(),
                c.entityType(), c.subtype(), c.value(), c.unit(), c.observedAt(), c.recordedAt(), c.sourceType(),
                c.privacyScope(), c.consentScope(), null);
    }
    private static HttpServer startBas() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/internal/auth/backend-authority/verify/", exchange -> {
                BAS_CALLS.incrementAndGet();
                FORWARDED_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                FORWARDED_SERVICE.set(exchange.getRequestHeaders().getFirst("X-BioCompass-Service"));
                FORWARDED_LENGTH.set(exchange.getRequestHeaders().getFirst("Content-Length"));
                FORWARDED_ENCODING.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
                FORWARDED_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var bytes = BAS_BODY.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(BAS_STATUS.get(), bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start(); return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
    @TestConfiguration @EnableConfigurationProperties(PkbRecordProperties.class)
    static class ClientConfiguration {
        @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
    }
}
