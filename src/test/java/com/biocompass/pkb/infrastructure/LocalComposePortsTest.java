package com.biocompass.pkb.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class LocalComposePortsTest {

    @ParameterizedTest(name = "loopback bindings with overridden ports: {0}")
    @ValueSource(booleans = {false, true})
    void publishesEveryDependencyOnlyOnLoopback(boolean overridePorts) throws Exception {
        var portNumbers = Map.of(
                "PKB_POSTGRES_PORT", overridePorts ? "15432" : "5432",
                "PKB_KAFKA_PORT", overridePorts ? "19092" : "9092",
                "PKB_MINIO_API_PORT", overridePorts ? "19000" : "9000",
                "PKB_MINIO_CONSOLE_PORT", overridePorts ? "19001" : "9001",
                "PKB_OPA_PORT", overridePorts ? "18181" : "8181");
        var expectedPorts = Map.of(
                "postgres", Map.of(5432, portNumbers.get("PKB_POSTGRES_PORT")),
                "kafka", Map.of(9092, portNumbers.get("PKB_KAFKA_PORT")),
                "minio", Map.of(9000, portNumbers.get("PKB_MINIO_API_PORT"),
                        9001, portNumbers.get("PKB_MINIO_CONSOLE_PORT")),
                "opa", Map.of(8181, portNumbers.get("PKB_OPA_PORT")));
        var output = Files.createTempFile("pkb-compose-ports-", ".json");
        var errors = Files.createTempFile("pkb-compose-ports-", ".stderr");
        Process process = null;
        try {
            var builder = new ProcessBuilder("docker", "compose", "--file", "compose.yaml",
                    "--env-file", ".env.example", "config", "--format", "json")
                    .redirectOutput(output.toFile())
                    .redirectError(errors.toFile());
            // Host settings must not change the defaults under test or load extra Compose files.
            builder.environment().keySet().removeIf(name -> name.startsWith("PKB_")
                    || name.startsWith("COMPOSE_"));
            if (overridePorts) {
                builder.environment().putAll(portNumbers);
            }
            process = builder.start();
            assertThat(process.waitFor(30, TimeUnit.SECONDS))
                    .as("Docker Compose configuration must complete within 30 seconds")
                    .isTrue();
            assertThat(process.exitValue())
                    .as("Docker Compose config must succeed; Docker CLI and Compose v2 are required")
                    .isZero();
            var services = JsonMapper.builder().build().readTree(Files.readString(output)).get("services");
            for (var expected : expectedPorts.entrySet()) {
                var service = services.get(expected.getKey());
                assertThat(service).as("Required service %s", expected.getKey()).isNotNull();
                var ports = service.get("ports");
                assertThat(ports).as("Published ports for %s", expected.getKey()).isNotNull();
                var actualPorts = new java.util.HashMap<Integer, String>();
                for (var port : ports) {
                    actualPorts.put(port.get("target").asInt(), port.get("published").asString());
                }
                assertThat(actualPorts).as("Required ports for %s", expected.getKey())
                        .containsAllEntriesOf(expected.getValue());
            }
            for (var service : services.properties()) {
                var ports = service.getValue().get("ports");
                if (ports == null) {
                    continue;
                }
                for (var port : ports) {
                    var hostIp = port.get("host_ip");
                    assertThat(hostIp).as("Explicit host binding for %s", service.getKey()).isNotNull();
                    assertThat(hostIp.asString()).as("Loopback binding for %s", service.getKey())
                            .isEqualTo("127.0.0.1");
                }
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }
}
