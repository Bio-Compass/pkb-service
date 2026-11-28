package com.biocompass.pkb.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    OpenAPI pkbOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BioCompass PKB Service API")
                        .version("0.0.1")
                        .description("Personal Knowledge Base service API."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME)
                        .addList("pkbServiceName").addList("pkbServiceToken"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque"))
                        .addSecuritySchemes("pkbServiceName", header("X-BioCompass-Service"))
                        .addSecuritySchemes("pkbServiceToken", header("X-BioCompass-Service-Token"))
                        .addSecuritySchemes("recordProducerBearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer"))
                        .addSecuritySchemes("recordUserAuthority", header("X-BioCompass-User-Token")));
    }

    @Bean
    OpenApiCustomizer recordAuthenticationContract() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (path.equals("/api/v1/internal/items") || path.startsWith("/api/v1/internal/items/")) {
                    item.readOperations().forEach(operation -> operation.setSecurity(List.of(new SecurityRequirement()
                            .addList("recordProducerBearer").addList("pkbServiceName").addList("recordUserAuthority"))));
                }
            });
        };
    }

    private static SecurityScheme header(String name) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(name);
    }
}
