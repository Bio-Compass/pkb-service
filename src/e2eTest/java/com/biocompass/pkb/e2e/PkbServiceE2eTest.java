package com.biocompass.pkb.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PkbServiceE2eTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String baseUrl = setting("PKB_E2E_BASE_URL", "pkb.e2e.base-url", "http://localhost:8080");
    private static final String jdbcUrl = setting("PKB_E2E_DATASOURCE_URL", "pkb.e2e.datasource-url",
            setting("PKB_DATASOURCE_URL", "pkb.datasource-url", "jdbc:postgresql://localhost:5432/pkb"));
    private static final String jdbcUsername = setting("PKB_E2E_DATASOURCE_USERNAME", "pkb.e2e.datasource-username",
            setting("PKB_DATASOURCE_USERNAME", "pkb.datasource-username",
                    setting("PKB_POSTGRES_USER", "pkb.postgres-user", "pkb")));
    private static final String jdbcPassword = setting("PKB_E2E_DATASOURCE_PASSWORD", "pkb.e2e.datasource-password",
            setting("PKB_DATASOURCE_PASSWORD", "pkb.datasource-password",
                    setting("PKB_POSTGRES_PASSWORD", "pkb.postgres-password", "pkb-local-password")));
    private static final int introspectionPort = Integer.parseInt(setting("PKB_E2E_AUTH_PORT", "pkb.e2e.auth-port", "8001"));
    private static final String introspectionPath = "/api/v1/internal/auth/token/introspect/";
    private static final String introspectionServiceName = setting(
            "PKB_E2E_INTROSPECTION_SERVICE_NAME",
            "pkb.e2e.introspection-service-name",
            setting("PKB_AUTH_INTROSPECTION_SERVICE_NAME", "pkb.auth.introspection.service-name", "pkb-service"));
    private static final String introspectionServiceToken = setting(
            "PKB_E2E_INTROSPECTION_SERVICE_TOKEN",
            "pkb.e2e.introspection-service-token",
            setting("PKB_AUTH_INTROSPECTION_SERVICE_TOKEN", "pkb.auth.introspection.service-token", "pkb-local-introspection-secret"));

    private static HttpServer introspectionServer;

    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();

    @BeforeAll
    static void serviceIsReachable() throws Exception {
        startIntrospectionServer();
        waitForHealthyService();
        try (var connection = connection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
        verifyOpaqueTokenAuthentication();
    }

    @AfterAll
    static void stopIntrospectionServer() {
        if (introspectionServer != null) {
            introspectionServer.stop(0);
        }
    }

    @BeforeEach
    void resetE2eData() throws SQLException {
        deleteThisTestData();
    }

    @AfterEach
    void cleanupE2eData() throws SQLException {
        deleteThisTestData();
    }

    @Test
    void exposesPublicHealthAndInfoEndpoints() throws Exception {
        var health = get("/actuator/health");
        var liveness = get("/actuator/health/liveness");
        var readiness = get("/actuator/health/readiness");
        var info = get("/actuator/info");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.jsonObject()).containsEntry("status", "UP");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.jsonObject()).containsEntry("status", "UP");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.jsonObject()).containsEntry("status", "UP");
        assertThat(info.statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsUnauthenticatedInactiveAndMalformedPkbRequests() throws Exception {
        assertThat(get(itemsPath(ownerUserId)).statusCode()).isEqualTo(401);
        assertThat(getWithBearer(itemsPath(ownerUserId), "inactive-token").statusCode()).isEqualTo(401);
        assertThat(get("/api/pkb/items", ownerUserId).statusCode()).isEqualTo(400);
        assertThat(get("/api/pkb/items?userId=not-a-uuid", ownerUserId).statusCode()).isEqualTo(400);
    }

    @Test
    void returnsUserScopedItemById() throws Exception {
        var item = SeedItem.observation(ownerUserId, "e2e-lookup", "hydration", "Water intake", observed("10:15:00"));
        insertItem(item);

        var response = get("/api/pkb/items/" + item.itemId() + "?userId=" + ownerUserId, ownerUserId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonObject())
                .containsEntry("itemId", item.itemId().toString())
                .containsEntry("userId", ownerUserId.toString())
                .containsEntry("entityType", "observation")
                .containsEntry("subtype", "hydration")
                .containsEntry("status", "active")
                .containsEntry("sourceType", "e2e")
                .containsEntry("sourceId", "e2e-lookup")
                .containsEntry("language", "en")
                .containsEntry("verificationStatus", "user_reported");
        Map<?, ?> payload = (Map<?, ?>) response.jsonObject().get("payload");
        assertThat(payload.get("label")).isEqualTo("Water intake");
        assertThat(payload.get("keyword")).isEqualTo("hydration");
        assertThat(response.jsonObject().get("consentScope")).isEqualTo(List.of("self-read"));
        assertThat(response.jsonObject().get("privacyScope")).isEqualTo(List.of("normal", "health"));
        assertThat(response.jsonObject().get("observedAt").toString()).startsWith("2026-07-04T10:15");
    }

    @Test
    void reportsMissingOwnedItemWithoutLeakingOtherUsersData() throws Exception {
        var otherUsersItem = SeedItem.observation(otherUserId, "e2e-other-owner", "hydration", "Other user", observed("09:00:00"));
        insertItem(otherUsersItem);

        var missingForOwner = get("/api/pkb/items/" + otherUsersItem.itemId() + "?userId=" + ownerUserId, ownerUserId);
        var missingItem = get("/api/pkb/items/" + UUID.randomUUID() + "?userId=" + ownerUserId, ownerUserId);

        assertThat(missingForOwner.statusCode()).isEqualTo(404);
        assertThat(missingForOwner.jsonObject()).containsEntry("code", "pkb_item_not_found");
        assertThat(missingItem.statusCode()).isEqualTo(404);
        assertThat(missingItem.jsonObject()).containsEntry("code", "pkb_item_not_found");
    }

    @Test
    void enforcesUserScopeAuthorization() throws Exception {
        var item = SeedItem.observation(ownerUserId, "e2e-authz", "hydration", "Owner data", observed("10:00:00"));
        insertItem(item);

        var denied = get("/api/pkb/items/" + item.itemId() + "?userId=" + ownerUserId, otherUserId);
        var allowedForStaff = getAsStaff(
                "/api/pkb/items/" + item.itemId() + "?userId=" + ownerUserId,
                staffUserId);

        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(allowedForStaff.statusCode()).isEqualTo(200);
        assertThat(allowedForStaff.jsonObject()).containsEntry("itemId", item.itemId().toString());
    }

    @Test
    void searchesUserItemsWithFiltersSortingAndPagination() throws Exception {
        var olderMatch = SeedItem.observation(ownerUserId, "e2e-search-older", "hydration", "Hydration before run", observed("08:00:00"));
        var newerMatch = SeedItem.observation(ownerUserId, "e2e-search-newer", "hydration", "Hydration after run", observed("12:00:00"));
        var subtypeMismatch = SeedItem.observation(ownerUserId, "e2e-search-subtype", "symptom", "Hydration headache", observed("13:00:00"));
        var statusMismatch = SeedItem.observation(ownerUserId, "e2e-search-status", "hydration", "Hydration draft", observed("14:00:00"))
                .withStatus("archived");
        var privacyMismatch = SeedItem.observation(ownerUserId, "e2e-search-privacy", "hydration", "Hydration private", observed("15:00:00"))
                .withPrivacyScope(List.of("restricted"));
        var textMismatch = SeedItem.observation(ownerUserId, "e2e-search-text", "hydration", "Protein snack", observed("16:00:00"));
        var otherUsersMatch = SeedItem.observation(otherUserId, "e2e-search-other", "hydration", "Hydration other", observed("17:00:00"));
        insertItems(olderMatch, newerMatch, subtypeMismatch, statusMismatch, privacyMismatch, textMismatch, otherUsersMatch);

        var response = get(path("/api/pkb/items", Map.of(
                "userId", ownerUserId.toString(),
                "entityType", "observation",
                "subtype", "hydration",
                "status", "active",
                "privacyScope", "normal",
                "text", "hydration",
                "observedFrom", observed("07:00:00").toString(),
                "observedUntil", observed("12:30:00").toString(),
                "limit", "1",
                "offset", "0")), ownerUserId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonArray())
                .extracting(item -> item.get("sourceId"))
                .containsExactly("e2e-search-newer");

        var secondPage = get(path("/api/pkb/items", Map.of(
                "userId", ownerUserId.toString(),
                "entityType", "observation",
                "subtype", "hydration",
                "status", "active",
                "privacyScope", "normal",
                "text", "hydration",
                "observedFrom", observed("07:00:00").toString(),
                "observedUntil", observed("12:30:00").toString(),
                "limit", "1",
                "offset", "1")), ownerUserId);

        assertThat(secondPage.statusCode()).isEqualTo(200);
        assertThat(secondPage.jsonArray())
                .extracting(item -> item.get("sourceId"))
                .containsExactly("e2e-search-older");
    }

    @Test
    void searchesByValidityWindowAndReturnsEmptyResultWhenNoItemsMatch() throws Exception {
        var currentlyValid = SeedItem.observation(ownerUserId, "e2e-valid-current", "hydration", "Current", observed("10:00:00"))
                .withValidity(observed("09:00:00"), observed("11:00:00"));
        var expired = SeedItem.observation(ownerUserId, "e2e-valid-expired", "hydration", "Expired", observed("10:30:00"))
                .withValidity(observed("07:00:00"), observed("08:00:00"));
        insertItems(currentlyValid, expired);

        var response = get(path("/api/pkb/items", Map.of(
                "userId", ownerUserId.toString(),
                "validAt", observed("10:00:00").toString(),
                "text", "current")), ownerUserId);
        var empty = get(path("/api/pkb/items", Map.of(
                "userId", ownerUserId.toString(),
                "text", "does-not-exist")), ownerUserId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonArray())
                .extracting(item -> item.get("sourceId"))
                .containsExactly("e2e-valid-current");
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.jsonArray()).isEmpty();
    }

    @Test
    void rejectsInvalidSearchParameters() throws Exception {
        assertThat(get(path("/api/pkb/items", Map.of(
                "userId", ownerUserId.toString(),
                "observedFrom", observed("12:00:00").toString(),
                "observedUntil", observed("11:00:00").toString())), ownerUserId)
                .statusCode()).isEqualTo(400);
        assertThat(get(itemsPath(ownerUserId) + "&limit=0", ownerUserId).statusCode()).isEqualTo(400);
        assertThat(get(itemsPath(ownerUserId) + "&limit=201", ownerUserId).statusCode()).isEqualTo(400);
        assertThat(get(itemsPath(ownerUserId) + "&offset=-1", ownerUserId).statusCode()).isEqualTo(400);
        assertThat(get(itemsPath(ownerUserId) + "&observedFrom=not-a-date", ownerUserId).statusCode()).isEqualTo(400);
        assertThat(get("/api/pkb/items/not-a-uuid?userId=" + ownerUserId, ownerUserId).statusCode()).isEqualTo(400);
    }

    private static void waitForHealthyService() throws InterruptedException {
        Exception lastException = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                var response = get("/actuator/health");
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (Exception exception) {
                lastException = exception;
            }
            Thread.sleep(1_000);
        }
        fail("""
                PKB service is not healthy at %s.
                Start it with:
                ./gradlew bootRun --args='--spring.profiles.active=local'
                """.formatted(baseUrl), lastException);
    }

    private static void verifyOpaqueTokenAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        var response = get(itemsPath(userId), userId);

        assertThat(response.statusCode())
                .withFailMessage("""
                        PKB opaque-token authentication is not configured for E2E.
                        Start the service with the local profile and ensure the introspection URL points to:
                        http://localhost:%s%s
                        Expected authenticated empty search to return 200, but got %s with body: %s
                        """, introspectionPort, introspectionPath, response.statusCode(), response.body())
                .isEqualTo(200);
    }

    private static HttpResult get(String path) throws Exception {
        return getWithBearer(path, null);
    }

    private static HttpResult get(String path, UUID actorUserId) throws Exception {
        return getWithBearer(path, tokenFor(actorUserId, false));
    }

    private static HttpResult getAsStaff(String path, UUID actorUserId) throws Exception {
        return getWithBearer(path, tokenFor(actorUserId, true));
    }

    private static HttpResult getWithBearer(String path, String bearerToken) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private static void startIntrospectionServer() throws IOException {
        introspectionServer = HttpServer.create(new InetSocketAddress("localhost", introspectionPort), 0);
        introspectionServer.createContext(introspectionPath, PkbServiceE2eTest::handleIntrospection);
        introspectionServer.start();
    }

    private static void handleIntrospection(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"active\":false}");
            return;
        }
        if (!("Bearer " + introspectionServiceToken).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                || !introspectionServiceName.equals(exchange.getRequestHeaders().getFirst("X-BioCompass-Service"))) {
            sendJson(exchange, 401, "{\"active\":false}");
            return;
        }

        try {
            Map<String, Object> request = objectMapper.readValue(exchange.getRequestBody(), new TypeReference<>() {});
            var token = request.get("token");
            var subject = token instanceof String value ? subject(value) : null;
            if (subject == null) {
                sendJson(exchange, 200, "{\"active\":false}");
                return;
            }

            sendJson(exchange, 200, objectMapper.writeValueAsString(Map.of(
                    "active", true,
                    "user_id", subject.userId().toString(),
                    "email", "e2e-%s@example.test".formatted(subject.userId()),
                    "email_verified", true,
                    "is_staff", subject.staff()
            )));
        } catch (RuntimeException exception) {
            sendJson(exchange, 400, "{\"active\":false}");
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, responseBody.length);
        try (var response = exchange.getResponseBody()) {
            response.write(responseBody);
        }
    }

    private static TokenSubject subject(String token) {
        boolean staff = token.startsWith("e2e-staff-");
        boolean user = token.startsWith("e2e-user-");
        if (!staff && !user) {
            return null;
        }
        try {
            return new TokenSubject(UUID.fromString(token.substring(staff ? "e2e-staff-".length() : "e2e-user-".length())), staff);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String tokenFor(UUID userId, boolean staff) {
        return (staff ? "e2e-staff-" : "e2e-user-") + userId;
    }

    private static String itemsPath(UUID userId) {
        return "/api/pkb/items?userId=" + userId;
    }

    private static String path(String path, Map<String, String> queryParameters) {
        StringBuilder builder = new StringBuilder(path).append('?');
        queryParameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (builder.charAt(builder.length() - 1) != '?') {
                        builder.append('&');
                    }
                    builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
                });
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static OffsetDateTime observed(String time) {
        return OffsetDateTime.parse("2026-07-04T" + time + "Z");
    }

    private static void insertItems(SeedItem... items) throws SQLException {
        for (SeedItem item : items) {
            insertItem(item);
        }
    }

    private static void insertItem(SeedItem item) throws SQLException {
        try (var connection = connection();
             var statement = connection.prepareStatement("""
                     INSERT INTO pkb_item (
                         pkb_item_id,
                         user_id,
                         entity_type,
                         subtype,
                         status,
                         payload,
                         source_type,
                         source_id,
                         observed_at,
                         ingested_at,
                         valid_from,
                         valid_until,
                         language,
                         consent_scope,
                         privacy_scope,
                         verification_status,
                         created_at,
                         updated_at
                     )
                     VALUES (?, ?, ?, ?, ?, ?::jsonb, 'e2e', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, item.itemId());
            statement.setObject(2, item.userId());
            statement.setString(3, item.entityType());
            statement.setString(4, item.subtype());
            statement.setString(5, item.status());
            statement.setString(6, item.payloadJson());
            statement.setString(7, item.sourceId());
            statement.setObject(8, item.observedAt());
            statement.setObject(9, item.ingestedAt());
            statement.setObject(10, item.validFrom());
            statement.setObject(11, item.validUntil());
            statement.setString(12, item.language());
            statement.setArray(13, connection.createArrayOf("text", item.consentScope().toArray(String[]::new)));
            statement.setArray(14, connection.createArrayOf("text", item.privacyScope().toArray(String[]::new)));
            statement.setString(15, item.verificationStatus());
            statement.setObject(16, item.createdAt());
            statement.setObject(17, item.updatedAt());
            statement.executeUpdate();
        }
    }

    private void deleteThisTestData() throws SQLException {
        try (var connection = connection();
             var statement = connection.prepareStatement("DELETE FROM pkb_item WHERE user_id IN (?, ?, ?)")) {
            statement.setObject(1, ownerUserId);
            statement.setObject(2, otherUserId);
            statement.setObject(3, staffUserId);
            statement.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
    }

    private static String setting(String environmentName, String propertyName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.strip();
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.strip();
        }
        return defaultValue;
    }

    private record HttpResult(int statusCode, String body) {

        Map<String, Object> jsonObject() throws IOException {
            return objectMapper.readValue(body, new TypeReference<>() {});
        }

        List<Map<String, Object>> jsonArray() throws IOException {
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    private record TokenSubject(UUID userId, boolean staff) {}

    private record SeedItem(
            UUID itemId,
            UUID userId,
            String entityType,
            String subtype,
            String status,
            String payloadJson,
            String sourceId,
            OffsetDateTime observedAt,
            OffsetDateTime ingestedAt,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            String language,
            List<String> consentScope,
            List<String> privacyScope,
            String verificationStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        static SeedItem observation(UUID userId, String sourceId, String subtype, String keyword, OffsetDateTime observedAt) {
            String escapedKeyword = keyword.replace("\\", "\\\\").replace("\"", "\\\"");
            return new SeedItem(
                    UUID.randomUUID(),
                    userId,
                    "observation",
                    subtype,
                    "active",
                    "{\"label\":\"%s\",\"keyword\":\"%s\",\"amount\":350,\"unit\":\"ml\"}"
                            .formatted(escapedKeyword, subtype),
                    sourceId,
                    observedAt,
                    observedAt.plusMinutes(1),
                    observedAt.minusHours(1),
                    observedAt.plusHours(1),
                    "en",
                    List.of("self-read"),
                    List.of("normal", "health"),
                    "user_reported",
                    observedAt.plusMinutes(2),
                    observedAt.plusMinutes(2));
        }

        SeedItem withStatus(String status) {
            return copy(status, privacyScope, validFrom, validUntil);
        }

        SeedItem withPrivacyScope(List<String> privacyScope) {
            return copy(status, privacyScope, validFrom, validUntil);
        }

        SeedItem withValidity(OffsetDateTime validFrom, OffsetDateTime validUntil) {
            return copy(status, privacyScope, validFrom, validUntil);
        }

        private SeedItem copy(
                String status,
                List<String> privacyScope,
                OffsetDateTime validFrom,
                OffsetDateTime validUntil) {
            return new SeedItem(
                    itemId,
                    userId,
                    entityType,
                    subtype,
                    status,
                    payloadJson,
                    sourceId,
                    observedAt,
                    ingestedAt,
                    validFrom,
                    validUntil,
                    language,
                    consentScope,
                    privacyScope,
                    verificationStatus,
                    createdAt,
                    updatedAt);
        }

        SeedItem {
            Objects.requireNonNull(itemId, "itemId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(sourceId, "sourceId must not be null");
        }
    }
}
