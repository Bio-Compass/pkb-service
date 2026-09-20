package com.biocompass.pkb.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biocompass.pkb.config.HttpClientConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = BioCompassAuClientIntegrationTest.TestApplication.class)
class BioCompassAuClientIntegrationTest {

    private static final AtomicInteger status = new AtomicInteger();
    private static final AtomicReference<String> responseBody = new AtomicReference<>();
    private static final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private static final AtomicReference<String> serviceHeader = new AtomicReference<>();
    private static final AtomicReference<String> requestBody = new AtomicReference<>();
    private static final HttpServer server = startServer();

    @Autowired
    private PkbAuthorizationGateway authorizationGateway;

    @DynamicPropertySource
    static void authorizationProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "biocompass.pkb.authorization.decision-url",
                () -> "http://localhost:" + server.getAddress().getPort() + "/v1/decision");
        registry.add("biocompass.pkb.authorization.service-name", () -> "pkb-service");
        registry.add("biocompass.pkb.authorization.service-token", () -> "service-secret");
        registry.add("biocompass.pkb.authorization.timeout", () -> "2s");
    }

    @BeforeEach
    void resetResponse() {
        status.set(200);
        responseBody.set("""
                {
                  "result": {
                    "allowed": true,
                    "decision_reference": "au:test-1",
                    "action": "write",
                    "target_user_id": "11111111-1111-1111-1111-111111111111",
                    "obligations": {}
                  }
                }
                """);
        authorizationHeader.set(null);
        serviceHeader.set(null);
        requestBody.set(null);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void callsAuWithServiceCredentialsAndSafeWriteContext() {
        var decision = authorizationGateway.decide(request());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.decisionReference()).isEqualTo("au:test-1");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer service-secret");
        assertThat(serviceHeader.get()).isEqualTo("pkb-service");
        assertThat(requestBody.get())
                .contains("command_id", "producer_service", "actor_id", "prior_decision_reference")
                .doesNotContain("end-user-bearer-token");
    }

    @Test
    void rejectsResponseWithoutDecision() {
        responseBody.set("{\"result\":null}");

        assertThatThrownBy(() -> authorizationGateway.decide(request()))
                .isInstanceOf(PkbAuthorizationInvalidDecisionException.class)
                .hasMessage("BioCompass AU returned no decision");
    }

    @Test
    void reportsAuHttpFailureAsUnavailable() {
        status.set(503);
        responseBody.set("{\"error\":\"temporarily unavailable\"}");

        assertThatThrownBy(() -> authorizationGateway.decide(request()))
                .isInstanceOf(PkbAuthorizationUnavailableException.class)
                .hasMessage("BioCompass AU decision request failed");
    }

    @Test
    void reportsAuClientRejectionAsPermanent() {
        status.set(403);
        responseBody.set("{\"error\":\"forbidden\"}");

        assertThatThrownBy(() -> authorizationGateway.decide(request()))
                .isInstanceOf(PkbAuthorizationInvalidDecisionException.class)
                .hasMessage("BioCompass AU rejected the decision request with HTTP 403");
    }

    @Test
    void treatsAuRateLimitAsTransient() {
        status.set(429);
        responseBody.set("{\"error\":\"rate limited\"}");

        assertThatThrownBy(() -> authorizationGateway.decide(request()))
                .isInstanceOf(PkbAuthorizationUnavailableException.class)
                .hasMessage("BioCompass AU decision request failed");
    }

    private static PkbAuthorizationRequest request() {
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return new PkbAuthorizationRequest(
                UUID.randomUUID(),
                "write",
                "pkb-service",
                new PkbAuthorizationRequest.Actor(
                        userId.toString(),
                        userId,
                        false,
                        Set.of("user"),
                        Set.of("pkb:write"),
                        "self"),
                new PkbAuthorizationRequest.Resource(
                        userId,
                        "CreatePkbItemCommand",
                        "manual",
                        java.util.List.of("self-read"),
                        java.util.List.of("health"),
                        java.util.Map.of()),
                "au:http-check");
    }

    private static HttpServer startServer() {
        try {
            var httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            httpServer.createContext("/v1/decision", exchange -> {
                authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                serviceHeader.set(exchange.getRequestHeaders().getFirst("X-BioCompass-Service"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var body = responseBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status.get(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            httpServer.start();
            return httpServer;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(PkbAuthorizationProperties.class)
    @Import({HttpClientConfig.class, BioCompassAuClient.class})
    static class TestApplication {
    }
}
