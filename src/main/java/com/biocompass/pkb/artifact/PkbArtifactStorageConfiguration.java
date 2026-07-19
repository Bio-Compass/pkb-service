package com.biocompass.pkb.artifact;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class PkbArtifactStorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "biocompass.pkb.storage", name = "bucket")
    S3Client pkbArtifactS3Client(PkbArtifactStorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(nonBlankOrDefault(properties.region(), "eu-central-1")))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
                        .build());

        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        var accessKeyConfigured = hasText(properties.accessKey());
        var secretKeyConfigured = hasText(properties.secretKey());
        if (accessKeyConfigured != secretKeyConfigured) {
            throw new IllegalStateException(
                    "biocompass.pkb.storage.access-key and biocompass.pkb.storage.secret-key must be configured together"
            );
        }

        if (accessKeyConfigured) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey().trim(), properties.secretKey().trim())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }

        return builder.build();
    }

    private static String nonBlankOrDefault(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
