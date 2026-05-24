package com.biocompass.pkb.local;

import com.biocompass.pkb.config.LocalInfrastructureProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class LocalInfrastructureContextTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg17-trixie")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("pkb")
            .withUsername("pkb")
            .withPassword("pkb-local-password");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-06-13T11-33-47Z"))
            .withEnv("MINIO_ROOT_USER", "pkb-local-access")
            .withEnv("MINIO_ROOT_PASSWORD", "pkb-local-secret")
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Container
    static final GenericContainer<?> opa = new GenericContainer<>(DockerImageName.parse("openpolicyagent/opa:1.16.2"))
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("infra/local/opa/policies").toAbsolutePath()), "/policies")
            .withCommand("run", "--server", "--addr=0.0.0.0:8181", "/policies")
            .withExposedPorts(8181)
            .waitingFor(Wait.forHttp("/health").forPort(8181));

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private LocalInfrastructureProperties localInfrastructureProperties;

    @DynamicPropertySource
    static void localInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("biocompass.pkb.opa.base-url", LocalInfrastructureContextTest::opaBaseUrl);
        registry.add("biocompass.pkb.opa.decision-path", () -> "/v1/data/biocompass/pkb/authz/allow");
        registry.add("biocompass.pkb.storage.endpoint", LocalInfrastructureContextTest::minioEndpoint);
        registry.add("biocompass.pkb.storage.region", () -> "eu-central-1");
        registry.add("biocompass.pkb.storage.bucket", () -> "pkb-local");
        registry.add("biocompass.pkb.storage.access-key", () -> "pkb-local-access");
        registry.add("biocompass.pkb.storage.secret-key", () -> "pkb-local-secret");
        registry.add("biocompass.pkb.storage.path-style-access-enabled", () -> "true");
    }

    @Test
    void contextLoadsWithContainerBackedLocalInfrastructureProperties() {
        assertThat(applicationContext).isNotNull();
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:postgresql://");
        assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).contains(":");

        assertThat(localInfrastructureProperties.opa().baseUrl()).isEqualTo(URI.create(opaBaseUrl()));
        assertThat(localInfrastructureProperties.opa().decisionPath()).isEqualTo("/v1/data/biocompass/pkb/authz/allow");
        assertThat(localInfrastructureProperties.storage().endpoint()).isEqualTo(URI.create(minioEndpoint()));
        assertThat(localInfrastructureProperties.storage().bucket()).isEqualTo("pkb-local");
        assertThat(localInfrastructureProperties.storage().pathStyleAccessEnabled()).isTrue();
    }

    @Test
    void testcontainerDependenciesAreReachable() throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {
            assertThat(connection.isValid(2)).isTrue();
        }

        try (var adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()))) {
            assertThat(adminClient.listTopics().names().get(10, TimeUnit.SECONDS)).isNotNull();
        }

        assertThat(get(minioEndpoint() + "/minio/health/ready").statusCode()).isEqualTo(200);
        assertThat(get(opaBaseUrl() + "/health").statusCode()).isEqualTo(200);
    }

    private static String minioEndpoint() {
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    }

    private static String opaBaseUrl() {
        return "http://" + opa.getHost() + ":" + opa.getMappedPort(8181);
    }

    private static HttpResponse<String> get(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
