package com.biocompass.pkb.local;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class LocalOpaPolicyTest {

    @Container
    static final GenericContainer<?> opa = new GenericContainer<>(DockerImageName.parse("openpolicyagent/opa:1.16.2"))
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("infra/local/opa/policies").toAbsolutePath()), "/policies")
            .withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
            .withExposedPorts(8181)
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @Test
    void allowsOwnerSelfAccessForNormalPrivacyScope() throws Exception {
        var response = evaluate("""
                {
                  "input": {
                    "actor": {"user_id": "user-1", "roles": ["user"]},
                    "action": "read",
                    "purpose": "self",
                    "resource": {"owner_user_id": "user-1", "privacy_scope": "normal"}
                  }
                }
                """);

        assertThat(response).contains("\"result\":true");
    }

    @Test
    void deniesOwnerAccessForRestrictedPrivacyScope() throws Exception {
        var response = evaluate("""
                {
                  "input": {
                    "actor": {"user_id": "user-1", "roles": ["user"]},
                    "action": "read",
                    "purpose": "self",
                    "resource": {"owner_user_id": "user-1", "privacy_scope": "restricted"}
                  }
                }
                """);

        assertThat(response).contains("\"result\":false");
    }

    @Test
    void allowsPkbAdminAccess() throws Exception {
        var response = evaluate("""
                {
                  "input": {
                    "actor": {"user_id": "admin-1", "roles": ["pkb_admin"]},
                    "action": "read",
                    "purpose": "operations",
                    "resource": {"owner_user_id": "user-1", "privacy_scope": "restricted"}
                  }
                }
                """);

        assertThat(response).contains("\"result\":true");
    }

    private static String evaluate(String inputJson) throws Exception {
        var request = HttpRequest.newBuilder(policyUri())
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(inputJson))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static URI policyUri() {
        return URI.create("http://" + opa.getHost() + ":" + opa.getMappedPort(8181)
                + "/v1/data/biocompass/pkb/authz/allow");
    }
}
