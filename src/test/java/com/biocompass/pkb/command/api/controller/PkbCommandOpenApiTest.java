package com.biocompass.pkb.command.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biocompass.pkb.command.api.mapper.PkbArtifactCommandMapper;
import com.biocompass.pkb.command.api.mapper.PkbItemCommandMapper;
import com.biocompass.pkb.command.api.mapper.PkbRelationshipCommandMapper;
import com.biocompass.pkb.command.api.service.PkbCommandIngressService;
import com.biocompass.pkb.command.api.web.PkbCommandHeaderArgumentResolver;
import com.biocompass.pkb.command.api.web.PkbCommandWebConfig;
import com.biocompass.pkb.config.OpenApiConfig;
import com.biocompass.pkb.config.SecurityConfig;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = PkbCommandOpenApiTest.TestApplication.class)
@AutoConfigureMockMvc
class PkbCommandOpenApiTest {

    private static final String[] COMMAND_PATHS = {
            "/api/pkb/commands/items",
            "/api/pkb/commands/items/{itemId}/supersessions",
            "/api/pkb/commands/relationships",
            "/api/pkb/commands/artifacts",
            "/api/pkb/commands/artifacts/{artifactId}/associations"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OpaqueTokenIntrospector opaqueTokenIntrospector;

    @MockitoBean
    private PkbCommandIngressService commandIngressService;

    @MockitoBean
    private PkbItemCommandMapper itemCommandMapper;

    @MockitoBean
    private PkbRelationshipCommandMapper relationshipCommandMapper;

    @MockitoBean
    private PkbArtifactCommandMapper artifactCommandMapper;

    @Test
    void documentsCommandAndCorrelationHeadersForEveryCommandEndpoint() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var openApi = objectMapper.readTree(response);

        for (String path : COMMAND_PATHS) {
            var operation = openApi.path("paths").path(path).path("post");
            var parameters = operation.path("parameters");
            assertHeader(parameters, "X-Command-Id", true);
            assertHeader(parameters, "X-Correlation-Id", false);
            assertThat(operation.path("responses").has("202"))
                    .as("OpenAPI 202 response for %s", path)
                    .isTrue();
            assertThat(operation.path("responses").has("200"))
                    .as("OpenAPI must not advertise 200 for %s", path)
                    .isFalse();
        }
    }

    private static void assertHeader(JsonNode parameters, String name, boolean required) {
        var header = StreamSupport.stream(parameters.spliterator(), false)
                .filter(parameter -> name.equals(parameter.path("name").asText()))
                .findFirst();

        assertThat(header)
                .as("OpenAPI header %s", name)
                .isPresent()
                .get()
                .satisfies(parameter -> {
                    assertThat(parameter.path("in").asText()).isEqualTo("header");
                    assertThat(parameter.path("required").asBoolean()).isEqualTo(required);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            OpenApiConfig.class,
            PkbCommandHeaderArgumentResolver.class,
            PkbCommandWebConfig.class,
            PkbItemCommandController.class,
            PkbRelationshipCommandController.class,
            PkbArtifactCommandController.class
    })
    static class TestApplication {
    }
}
