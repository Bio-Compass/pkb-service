package com.biocompass.pkb.artifact;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "biocompass.pkb.storage")
public record PkbArtifactStorageProperties(
        URI endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccessEnabled
) {
}
