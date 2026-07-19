package com.biocompass.pkb.artifact;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbArtifactStorageConfigurationTest {

    private final PkbArtifactStorageConfiguration configuration = new PkbArtifactStorageConfiguration();

    @Test
    void rejectsAccessKeyWithoutSecretKey() {
        var properties = properties("access-key", null);

        assertThatThrownBy(() -> configuration.pkbArtifactS3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured together");
    }

    @Test
    void rejectsSecretKeyWithoutAccessKey() {
        var properties = properties(null, "secret-key");

        assertThatThrownBy(() -> configuration.pkbArtifactS3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured together");
    }

    @Test
    void createsClientWithBothStaticCredentialValuesConfigured() {
        var properties = properties("access-key", "secret-key");

        try (var client = configuration.pkbArtifactS3Client(properties)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void createsClientWithDefaultCredentialChainWhenNoStaticCredentialsConfigured() {
        var properties = properties(null, null);

        try (var client = configuration.pkbArtifactS3Client(properties)) {
            assertThat(client).isNotNull();
        }
    }

    private static PkbArtifactStorageProperties properties(String accessKey, String secretKey) {
        return new PkbArtifactStorageProperties(
                URI.create("http://localhost:9000"),
                "eu-central-1",
                "pkb-local",
                accessKey,
                secretKey,
                true
        );
    }
}
