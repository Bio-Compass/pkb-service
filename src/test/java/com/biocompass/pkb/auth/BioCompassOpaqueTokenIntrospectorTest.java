package com.biocompass.pkb.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class BioCompassOpaqueTokenIntrospectorTest {

    @Test
    void convertsActiveBioCompassResponseToAuthenticatedPrincipal() {
        var client = new StubIntrospectionClient(
                new BioCompassTokenIntrospectionResponse(
                        true,
                        "user-1",
                        "user@example.com",
                        true,
                        true
                )
        );
        var introspector = new BioCompassOpaqueTokenIntrospector(client, authProperties());

        var principal = introspector.introspect("access-token");

        assertThat(client.token.get()).isEqualTo("access-token");
        assertThat(principal.getName()).isEqualTo("user-1");
        assertThat((Object) principal.getAttribute("user_id")).isEqualTo("user-1");
        assertThat((Object) principal.getAttribute("email")).isEqualTo("user@example.com");
        assertThat((Object) principal.getAttribute("email_verified")).isEqualTo(true);
        assertThat((Object) principal.getAttribute("is_staff")).isEqualTo(true);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_user", "ROLE_staff");
    }

    @Test
    void rejectsInactiveBioCompassResponse() {
        var introspector = new BioCompassOpaqueTokenIntrospector(
                new StubIntrospectionClient(BioCompassTokenIntrospectionResponse.inactive()),
                authProperties()
        );

        assertThatExceptionOfType(BadOpaqueTokenException.class)
                .isThrownBy(() -> introspector.introspect("inactive-token"));
    }

    private static PkbAuthProperties authProperties() {
        return new PkbAuthProperties(null, "self");
    }

    private static class StubIntrospectionClient extends BioCompassTokenIntrospectionClient {

        private final AtomicReference<String> token = new AtomicReference<>();
        private final BioCompassTokenIntrospectionResponse response;

        StubIntrospectionClient(BioCompassTokenIntrospectionResponse response) {
            super(authProperties(), RestClient.builder());
            this.response = response;
        }

        @Override
        public BioCompassTokenIntrospectionResponse introspect(String token) {
            this.token.set(token);
            return response;
        }
    }
}
