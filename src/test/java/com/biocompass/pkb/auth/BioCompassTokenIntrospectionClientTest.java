package com.biocompass.pkb.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class BioCompassTokenIntrospectionClientTest {

    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("""
            {"active":true,"user_id":"user-1","email":"user@example.com","email_verified":true,"is_staff":false}
            """);
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private final AtomicReference<String> serviceHeader = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private HttpServer server;
    private URI introspectionUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/internal/auth/token/introspect/", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            serviceHeader.set(exchange.getRequestHeaders().getFirst("X-BioCompass-Service"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        introspectionUrl = URI.create("http://localhost:" + server.getAddress().getPort()
                + "/api/v1/internal/auth/token/introspect/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsTokenToBioCompassAuthWithInternalServiceCredentials() {
        var client = client("service-secret");

        var response = client.introspect("user-access-token");

        assertThat(response.active()).isTrue();
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.staff()).isFalse();
        assertThat(authorizationHeader.get()).isEqualTo("Bearer service-secret");
        assertThat(serviceHeader.get()).isEqualTo("pkb-service");
        assertThat(requestBody.get()).contains("\"token\":\"user-access-token\"");
    }

    @Test
    void returnsInactiveResponseWhenBioCompassAuthMarksTokenInactive() {
        responseBody.set("{\"active\":false}");
        var client = client("service-secret");

        var response = client.introspect("inactive-token");

        assertThat(response.active()).isFalse();
    }

    @Test
    void failsWhenBioCompassAuthRejectsTheInternalClient() {
        status.set(401);
        responseBody.set("{\"detail\":\"Unauthorized.\"}");
        var client = client("wrong-secret");

        assertThatExceptionOfType(OAuth2IntrospectionException.class)
                .isThrownBy(() -> client.introspect("user-access-token"));
    }

    @Test
    void failsWhenInternalServiceTokenIsNotConfigured() {
        var client = client("");

        assertThatExceptionOfType(OAuth2IntrospectionException.class)
                .isThrownBy(() -> client.introspect("user-access-token"));
    }

    private BioCompassTokenIntrospectionClient client(String serviceToken) {
        return new BioCompassTokenIntrospectionClient(
                new PkbAuthProperties(
                        new PkbAuthProperties.Introspection(introspectionUrl, "pkb-service", serviceToken, 5),
                        "self"
                ),
                RestClient.builder()
        );
    }
}
