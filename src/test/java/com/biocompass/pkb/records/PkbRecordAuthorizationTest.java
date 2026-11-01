package com.biocompass.pkb.records;

import com.biocompass.pkb.authorization.PkbAuthorizationDecision;
import com.biocompass.pkb.authorization.PkbAuthorizationGateway;
import com.biocompass.pkb.authorization.PkbAuthorizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PkbRecordAuthorizationTest.TestApplication.class)
class PkbRecordAuthorizationTest {
    private static final UUID USER = UUID.randomUUID();
    private static final PkbRecordAuthority AUTHORITY = new PkbRecordAuthority(USER, "verified-actor", "producer-service",
            Set.of("hydration:intake:read"), "self");
    @Autowired private PkbRecordAuthorization authorization;
    @MockitoBean private PkbAuthorizationGateway gateway;

    @BeforeEach void allow() {
        when(gateway.decide(any())).thenAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return new PkbAuthorizationDecision(true, "decision-current", request.action(), USER, Map.of());
        });
    }

    @Test void sendsVerifiedActorProducerCategoryGovernanceAndIdentifierToAu() {
        var id = UUID.randomUUID();
        assertThat(authorization.authorize(AUTHORITY, "read", "nutrition_intake", "water", "device",
                List.of("restricted", "health"), List.of("consent-1"), id)).isEqualTo("decision-current");
        var capture = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway).decide(capture.capture());
        var request = capture.getValue();
        assertThat(request.commandId()).isNotNull();
        assertThat(request.action()).isEqualTo("read");
        assertThat(request.producerService()).isEqualTo("producer-service");
        assertThat(request.priorDecisionReference()).isNull();
        assertThat(request.actor().actorId()).isEqualTo("verified-actor");
        assertThat(request.actor().userId()).isEqualTo(USER);
        assertThat(request.actor().staff()).isFalse();
        assertThat(request.actor().roles()).isEmpty();
        assertThat(request.actor().scopes()).isEqualTo(AUTHORITY.permissions());
        assertThat(request.actor().purposeOfUse()).isEqualTo("self");
        assertThat(request.resource().targetUserId()).isEqualTo(USER);
        assertThat(request.resource().commandType()).isEqualTo("source_record");
        assertThat(request.resource().sourceType()).isEqualTo("device");
        assertThat(request.resource().privacyScope()).containsExactly("restricted", "health");
        assertThat(request.resource().consentScope()).containsExactly("consent-1");
        assertThat(request.resource().identifiers()).containsExactlyEntriesOf(Map.of("item_id", id));
        assertThat(request.resource().attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "source_system", "producer-service", "entity_type", "nutrition_intake", "subtype", "water"));
    }

    @Test void aggregateAuthorizationKeepsOperationAndUsesActualItemWithReadAuthority() {
        var id = UUID.randomUUID();
        authorization.authorize(AUTHORITY, "aggregate", "nutrition_intake", "water", "device", List.of("health"), List.of(), id);
        var capture = ArgumentCaptor.forClass(PkbAuthorizationRequest.class);
        verify(gateway).decide(capture.capture());
        assertThat(capture.getValue().action()).isEqualTo("aggregate");
        assertThat(capture.getValue().actor().scopes()).containsExactly("hydration:intake:read");
        assertThat(capture.getValue().resource().identifiers()).containsExactlyEntriesOf(Map.of("item_id", id));
    }

    @Test void denialFailsClosed() {
        doReturn(new PkbAuthorizationDecision(false, "deny", "read", USER, Map.of())).when(gateway).decide(any());
        assertFailure(403, "forbidden");
    }

    @Test void missingDecisionFailsClosed() {
        doReturn(null).when(gateway).decide(any());
        assertFailure(503, "unavailable");
    }

    @Test void decisionForAnotherActionFailsClosed() {
        doReturn(new PkbAuthorizationDecision(true, "allow", "aggregate", USER, Map.of())).when(gateway).decide(any());
        assertFailure(503, "unavailable");
    }

    @Test void decisionForAnotherOwnerFailsClosed() {
        doReturn(new PkbAuthorizationDecision(true, "allow", "read", UUID.randomUUID(), Map.of())).when(gateway).decide(any());
        assertFailure(503, "unavailable");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = " ")
    void missingDecisionReferenceFailsClosed(String reference) {
        doReturn(new PkbAuthorizationDecision(true, reference, "read", USER, Map.of())).when(gateway).decide(any());
        assertFailure(503, "unavailable");
    }

    @Test void unsupportedObligationCannotBeSilentlyIgnored() {
        doReturn(new PkbAuthorizationDecision(true, "allow", "read", USER, Map.of("redact", true))).when(gateway).decide(any());
        assertFailure(503, "unavailable");
    }

    private void assertFailure(int status, String code) {
        assertThatThrownBy(() -> authorization.authorize(AUTHORITY, "read", "nutrition_intake", "water", "device",
                List.of("health"), List.of(), UUID.randomUUID())).isInstanceOfSatisfying(PkbRecordFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(status);
            assertThat(failure.code()).isEqualTo(code);
        });
    }

    @Configuration(proxyBeanMethods = false) @Import(PkbRecordAuthorization.class)
    static class TestApplication {}
}
