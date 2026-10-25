package com.biocompass.pkb.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PkbRecordAuthorityClient {
    private final PkbRecordProperties properties;
    private final RestClient client;
    public PkbRecordAuthorityClient(PkbRecordProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(properties.timeout());
        // Django WSGI requires Content-Length to expose the JSON request body.
        client = builder.clone().requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }
    public PkbRecordAuthority authenticate(String producer, String authorization, String token) {
        if (!StringUtils.hasText(producer) || authorization == null || !authorization.startsWith("Bearer ")
                || !StringUtils.hasText(token) || token.length() > 16384)
            throw new PkbRecordFailure(401, "unauthorized");
        String expected = properties.serviceTokens().get(producer);
        if (!StringUtils.hasText(expected) || !same(expected, authorization.substring(7))
                || properties.serviceTokens().entrySet().stream().anyMatch(entry ->
                    !entry.getKey().equals(producer) && same(expected, entry.getValue())))
            throw new PkbRecordFailure(401, "unauthorized");
        if (properties.authorityUrl() == null || !StringUtils.hasText(properties.authorityServiceToken())
                || properties.serviceTokens().values().stream().anyMatch(value -> same(value, properties.authorityServiceToken())))
            throw new PkbRecordFailure(503, "unavailable");
        try {
            var result = client.post().uri(properties.authorityUrl())
                    .header("Authorization", "Bearer " + properties.authorityServiceToken())
                    .header("X-BioCompass-Service", "pkb-service")
                    .body(Map.of("token", token, "resource_service", producer)).retrieve().body(Response.class);
            if (result != null && Boolean.FALSE.equals(result.active())) throw new PkbRecordFailure(401, "unauthorized");
            if (result == null || !Boolean.TRUE.equals(result.active()) || result.userId() == null
                    || !producer.equals(result.resourceService()) || !StringUtils.hasText(result.actorId())
                    || result.permissions() == null || result.permissions().isEmpty()
                    || result.permissions().stream().anyMatch(value -> !StringUtils.hasText(value))
                    || !"self".equals(result.purposeOfUse())) throw new PkbRecordFailure(503, "unavailable");
            return new PkbRecordAuthority(result.userId(), result.actorId(), producer,
                    Set.copyOf(result.permissions()), result.purposeOfUse());
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new PkbRecordFailure(503, "unavailable");
        }
    }
    private static boolean same(String first, String second) {
        return second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
    record Response(Boolean active, @JsonProperty("user_id") UUID userId, @JsonProperty("actor_id") String actorId,
            @JsonProperty("resource_service") String resourceService, List<String> permissions,
            @JsonProperty("purpose_of_use") String purposeOfUse) {}
}
