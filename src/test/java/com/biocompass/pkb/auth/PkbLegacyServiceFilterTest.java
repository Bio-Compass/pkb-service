package com.biocompass.pkb.auth;

import com.biocompass.pkb.authorization.PkbAuthorizationProperties;
import com.biocompass.pkb.records.PkbRecordProperties;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class PkbLegacyServiceFilterTest {
    private static final String SECRET = "fixture-legacy-private";

    @AfterEach void clearRequestContext() { RequestContextHolder.resetRequestAttributes(); }

    @Test void emptyConfigurationDeniesEveryCaller() throws Exception {
        var response = new MockHttpServletResponse();
        filter(Map.of()).doFilter(request(), response, (r, s) -> fail("Unconfigured caller reached the application"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).doesNotContain(SECRET, "approved-client");
    }

    @ParameterizedTest @ValueSource(strings = {"service", "token", "wrong-service", "wrong-token", "duplicate-service", "duplicate-token"})
    void missingWrongOrAmbiguousCredentialsFailBeforeApplication(String defect) throws Exception {
        var request = request();
        switch (defect) {
            case "service" -> request.removeHeader(PkbLegacyServiceFilter.SERVICE_HEADER);
            case "token" -> request.removeHeader(PkbLegacyServiceFilter.TOKEN_HEADER);
            case "wrong-service" -> {
                request.removeHeader(PkbLegacyServiceFilter.SERVICE_HEADER);
                request.addHeader(PkbLegacyServiceFilter.SERVICE_HEADER, "spoofed-client");
            }
            case "wrong-token" -> {
                request.removeHeader(PkbLegacyServiceFilter.TOKEN_HEADER);
                request.addHeader(PkbLegacyServiceFilter.TOKEN_HEADER, "user-bearer-token");
            }
            case "duplicate-service" -> request.addHeader(PkbLegacyServiceFilter.SERVICE_HEADER, "approved-client");
            case "duplicate-token" -> request.addHeader(PkbLegacyServiceFilter.TOKEN_HEADER, SECRET);
            default -> throw new AssertionError(defect);
        }
        var response = new MockHttpServletResponse();
        filter(Map.of("approved-client", SECRET)).doFilter(request, response, (r, s) -> fail("Invalid workload reached application"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(PkbLegacyServiceFilter.CALLER_ATTRIBUTE)).isNull();
    }

    @Test void callerIsResolvedOnlyInsideAnAuthenticatedRequestAndRemovedOnFailure() {
        var request = request();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var resolver = new PkbServiceCallerResolver();
        assertThatThrownBy(resolver::resolve).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> filter(Map.of("approved-client", SECRET)).doFilter(request,
                new MockHttpServletResponse(), (r, s) -> {
                    assertThat(resolver.resolve()).isEqualTo("approved-client");
                    throw new IllegalStateException("Application failure");
                })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(resolver::resolve).isInstanceOf(ResponseStatusException.class);
        RequestContextHolder.resetRequestAttributes();
        assertThatThrownBy(resolver::resolve).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"fixture-record", "fixture-authority", "fixture-introspection", "fixture-au"})
    void credentialsCannotBeReusedAcrossCapabilities(String token) {
        assertThatThrownBy(() -> filter(Map.of("approved-client", token)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(token);
    }

    @Test void aliasesCannotShareCredentialsAndPropertiesNeverPrintThem() {
        assertThatThrownBy(() -> filter(Map.of("first", SECRET, "second", SECRET)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(SECRET);
        assertThatThrownBy(() -> filter(Map.of("first", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filter(Map.of("first", " " + SECRET)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PkbLegacyServiceProperties(Map.of("first", SECRET)).toString()).doesNotContain(SECRET);
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/pkb/commands/items");
        request.addHeader(PkbLegacyServiceFilter.SERVICE_HEADER, "approved-client");
        request.addHeader(PkbLegacyServiceFilter.TOKEN_HEADER, SECRET);
        return request;
    }

    private static PkbLegacyServiceFilter filter(Map<String, String> credentials) {
        return new PkbLegacyServiceFilter(new PkbLegacyServiceProperties(credentials),
                new PkbRecordProperties(Map.of("record-client", "fixture-record"), null, "fixture-authority", null),
                new PkbAuthProperties(new PkbAuthProperties.Introspection(null, null, "fixture-introspection", null), null),
                new PkbAuthorizationProperties(null, null, "fixture-au", null));
    }
}
