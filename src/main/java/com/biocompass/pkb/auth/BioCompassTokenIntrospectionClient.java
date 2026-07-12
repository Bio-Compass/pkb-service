package com.biocompass.pkb.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

@Component
public class BioCompassTokenIntrospectionClient {

    private static final String SERVICE_HEADER = "X-BioCompass-Service";

    private final PkbAuthProperties.Introspection introspectionProperties;
    private final RestClient restClient;

    BioCompassTokenIntrospectionClient(PkbAuthProperties authProperties, RestClient.Builder restClientBuilder) {
        this.introspectionProperties = authProperties.introspection();
        var requestFactory = new SimpleClientHttpRequestFactory();
        var timeout = Duration.ofSeconds(introspectionProperties.timeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    public BioCompassTokenIntrospectionResponse introspect(String token) {
        if (!StringUtils.hasText(introspectionProperties.serviceToken())) {
            throw new OAuth2IntrospectionException("BioCompass auth introspection service token is not configured");
        }

        try {
            var response = restClient
                    .post()
                    .uri(introspectionProperties.url())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + introspectionProperties.serviceToken())
                    .header(SERVICE_HEADER, introspectionProperties.serviceName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", token))
                    .retrieve()
                    .body(JsonNode.class);

            return parseResponse(response);
        } catch (RestClientException ex) {
            throw new OAuth2IntrospectionException("BioCompass auth introspection request failed", ex);
        }
    }

    private static BioCompassTokenIntrospectionResponse parseResponse(JsonNode response) {
        if (response == null || response.get("active") == null || !response.get("active").asBoolean(false)) {
            return BioCompassTokenIntrospectionResponse.inactive();
        }

        var userId = stringField(response, "user_id");
        if (!StringUtils.hasText(userId)) {
            throw new OAuth2IntrospectionException("BioCompass auth introspection response is missing user_id");
        }

        return new BioCompassTokenIntrospectionResponse(
                true,
                userId,
                stringField(response, "email"),
                booleanField(response, "email_verified"),
                booleanField(response, "is_staff")
        );
    }

    private static String stringField(JsonNode response, String fieldName) {
        var value = response.get(fieldName);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static boolean booleanField(JsonNode response, String fieldName) {
        var value = response.get(fieldName);
        return value != null && value.asBoolean(false);
    }
}
