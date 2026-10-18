package com.biocompass.pkb.authorization;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BioCompassAuClient implements PkbAuthorizationGateway {

    private static final String SERVICE_HEADER = "X-BioCompass-Service";

    private final PkbAuthorizationProperties properties;
    private final RestClient restClient;

    BioCompassAuClient(PkbAuthorizationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    @Override
    public PkbAuthorizationDecision decide(PkbAuthorizationRequest request) {
        if (properties.decisionUrl() == null) {
            throw new PkbAuthorizationUnavailableException("BioCompass AU decision URL is not configured");
        }

        try {
            var requestSpec = restClient.post()
                    .uri(properties.decisionUrl())
                    .header(SERVICE_HEADER, properties.serviceName())
                    .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(properties.serviceToken())) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceToken());
            }
            var response = requestSpec
                    .body(Map.of("input", request))
                    .retrieve()
                    .body(DecisionResponse.class);
            if (response == null || response.result() == null) {
                throw new PkbAuthorizationInvalidDecisionException("BioCompass AU returned no decision");
            }
            return response.result();
        } catch (RestClientResponseException exception) {
            if (isPermanentClientFailure(exception.getStatusCode().value())) {
                throw new PkbAuthorizationInvalidDecisionException(
                        "BioCompass AU rejected the decision request with HTTP "
                                + exception.getStatusCode().value(),
                        exception);
            }
            throw new PkbAuthorizationUnavailableException("BioCompass AU decision request failed", exception);
        } catch (RestClientException exception) {
            throw new PkbAuthorizationUnavailableException("BioCompass AU decision request failed", exception);
        }
    }

    private static boolean isPermanentClientFailure(int statusCode) {
        return statusCode >= 400
                && statusCode < 500
                && statusCode != 408
                && statusCode != 425
                && statusCode != 429;
    }

    private record DecisionResponse(PkbAuthorizationDecision result) {}
}
