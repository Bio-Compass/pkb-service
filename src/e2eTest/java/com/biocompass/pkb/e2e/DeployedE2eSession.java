package com.biocompass.pkb.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

final class DeployedE2eSession {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UUID userId;
    private final String accessToken;

    private DeployedE2eSession(UUID userId, String accessToken) {
        this.userId = userId;
        this.accessToken = accessToken;
    }

    static void validate(Map<String, String> environment) {
        if (!"testing".equals(environment.get("PKB_E2E_NAMESPACE"))) {
            throw new IllegalArgumentException("Deployed E2E must target namespace testing");
        }
        var access = environment.getOrDefault("PKB_E2E_ACCESS_MODE", "port-forward");
        if (!access.equals("port-forward") && !access.equals("cluster")) {
            throw new IllegalArgumentException("Unknown deployed E2E access mode");
        }
        for (var key : new String[]{"PKB_E2E_BASE_URL", "PKB_E2E_AUTH_URL"}) {
            var url = URI.create(required(environment, key));
            var expectedHost = key.equals("PKB_E2E_BASE_URL") ? "pkb-service" : "bio-compass-auth";
            var validAddress = access.equals("cluster")
                    ? expectedHost.equals(url.getHost()) && (url.getPort() == -1 || url.getPort() == 80)
                    : "127.0.0.1".equals(url.getHost()) && url.getPort() > 0 && url.getPort() <= 65535;
            if (!"http".equals(url.getScheme()) || !validAddress
                    || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null
                    || !(url.getPath().isEmpty() || url.getPath().equals("/"))) {
                throw new IllegalArgumentException(access.equals("cluster")
                        ? "Cluster E2E requires the exact testing PKB and BAS service URLs"
                        : "Deployed E2E requires loopback port-forward URLs");
            }
        }
        required(environment, "PKB_E2E_USERNAME");
        required(environment, "PKB_E2E_PASSWORD");
        required(environment, "PKB_E2E_SERVICE_NAME");
        required(environment, "PKB_E2E_SERVICE_TOKEN");
    }

    static DeployedE2eSession login(Map<String, String> environment, HttpClient client)
            throws IOException, InterruptedException {
        validate(environment);
        var base = environment.get("PKB_E2E_AUTH_URL").replaceAll("/$", "");
        var loginRequest = basRequest(base + "/api/v1/auth/token/")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(Map.of(
                        "email", environment.get("PKB_E2E_USERNAME"),
                        "password", environment.get("PKB_E2E_PASSWORD")))))
                .build();
        var login = send(client, loginRequest, "login");
        var token = login.path("access").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("BAS login did not return an access token");
        }
        var user = send(client, basRequest(base + "/api/v1/me/")
                .header("Authorization", "Bearer " + token).GET().build(), "identity lookup");
        if (!user.path("is_email_verified").asBoolean(false)) {
            throw new IllegalStateException("BAS did not return a verified test user");
        }
        try {
            return new DeployedE2eSession(UUID.fromString(user.path("id").asText("")), token);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("BAS did not return a valid test user ID");
        }
    }

    private static HttpRequest.Builder basRequest(String url) {
        // Use BAS's internal host/scheme metadata on the validated testing network path.
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Host", "bio-compass-auth").header("X-Forwarded-Proto", "https");
    }

    private static JsonNode send(HttpClient client, HttpRequest request, String operation)
            throws IOException, InterruptedException {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("BAS " + operation + " failed (HTTP " + response.statusCode() + ")");
        }
        try {
            var body = JSON.readTree(response.body());
            if (body != null && body.isObject()) {
                return body;
            }
        } catch (IOException ignored) {
            // Do not put response bodies or credentials in CI diagnostics.
        }
        throw new IllegalStateException("BAS " + operation + " did not return a JSON object");
    }

    private static String required(Map<String, String> environment, String key) {
        var value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Testing E2E requires " + key);
        }
        return value;
    }

    UUID userId() { return userId; }
    String accessToken() { return accessToken; }
}
