package com.biocompass.pkb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "biocompass.pkb")
public record LocalInfrastructureProperties(
        Opa opa,
        Storage storage
) {

    public record Opa(
            URI baseUrl,
            String decisionPath
    ) {
    }

    public record Storage(
            URI endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            boolean pathStyleAccessEnabled
    ) {
    }
}
