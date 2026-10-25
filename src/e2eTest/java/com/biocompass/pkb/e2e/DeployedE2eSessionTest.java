package com.biocompass.pkb.e2e;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeployedE2eSessionTest {
    private HttpServer server;
    private Map<String, String> environment;
    private final AtomicInteger loginStatus = new AtomicInteger(200);
    private String loginBody = "{\"access\":\"private-token\"}";
    private String userBody;
    private final UUID userId = UUID.randomUUID();
    private String loginRequestBody;
    private String authorization;
    private String host;
    private String scheme;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startBasFixture() throws Exception {
        userBody = "{\"id\":\"" + userId + "\",\"is_email_verified\":true}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/token/", exchange -> {
            loginRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            host = exchange.getRequestHeaders().getFirst("Host");
            scheme = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
            var body = loginBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "/api/v1/me/");
            exchange.sendResponseHeaders(loginStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/v1/me/", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            var body = userBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        environment = new HashMap<>(Map.of(
                "PKB_E2E_NAMESPACE", "testing", "PKB_E2E_BASE_URL", "http://127.0.0.1:18080",
                "PKB_E2E_AUTH_URL", "http://127.0.0.1:" + server.getAddress().getPort(),
                "PKB_E2E_USERNAME", "synthetic@example.test", "PKB_E2E_PASSWORD", "private-password"));
    }

    @AfterEach
    void stopBasFixture() { server.stop(0); }

    @Test
    void rejectsProductionBeforeSendingCredentials() {
        environment.put("PKB_E2E_NAMESPACE", "prod");
        assertThatThrownBy(() -> DeployedE2eSession.login(environment, client)).hasMessageContaining("namespace testing");
        assertThat(loginRequestBody).isNull();
    }

    @Test
    void rejectsNonLoopbackAndCredentialBearingUrls() {
        for (var url : new String[]{"https://example.test", "http://user:secret@127.0.0.1:80", "http://127.0.0.1:80/path"}) {
            environment.put("PKB_E2E_AUTH_URL", url);
            assertThatThrownBy(() -> DeployedE2eSession.login(environment, client)).hasMessageContaining("loopback");
        }
        assertThat(loginRequestBody).isNull();
    }

    @Test
    void requiresAccountCredentials() {
        environment.put("PKB_E2E_PASSWORD", " ");
        assertThatThrownBy(() -> DeployedE2eSession.login(environment, client)).hasMessageContaining("PKB_E2E_PASSWORD");
        assertThat(loginRequestBody).isNull();
    }

    @Test
    void logsInAndResolvesVerifiedIdentityThroughRealHttp() throws Exception {
        var session = DeployedE2eSession.login(environment, client);
        assertThat(session.userId()).isEqualTo(userId);
        assertThat(session.accessToken()).isEqualTo("private-token");
        assertThat(loginRequestBody).contains("synthetic@example.test", "private-password");
        assertThat(authorization).isEqualTo("Bearer private-token");
        assertThat(host).isEqualTo("bio-compass-auth");
        assertThat(scheme).isEqualTo("https");
    }

    @Test
    void failsAuthenticationWithoutExposingResponseBody() {
        loginStatus.set(401);
        loginBody = "private-password private-token";
        assertThatThrownBy(() -> DeployedE2eSession.login(environment, client))
                .hasMessageContaining("HTTP 401").hasMessageNotContaining("private-password").hasMessageNotContaining("private-token");
    }

    @Test
    void rejectsRedirectsAndDoesNotForwardCredentials() {
        loginStatus.set(302);
        assertThatThrownBy(() -> DeployedE2eSession.login(environment, client)).hasMessageContaining("HTTP 302");
        assertThat(authorization).isNull();
    }

    @Test
    void rejectsUnverifiedUsersAndMissingAccessTokens() {
        userBody = "{\"id\":\"" + userId + "\",\"is_email_verified\":false}";
        assertThatThrownBy(() -> DeployedE2eSession.login(environment, client)).hasMessageContaining("verified test user");
        loginBody = "{}";
        assertThatThrownBy(() -> DeployedE2eSession.login(environment, client)).hasMessageContaining("access token");
    }
}
