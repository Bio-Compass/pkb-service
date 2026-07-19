package com.biocompass.pkb.query;

import com.biocompass.pkb.config.OpenApiConfig;
import com.biocompass.pkb.config.SecurityConfig;
import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import com.biocompass.pkb.security.BioCompassActorMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = PkbItemQueryControllerSecurityTest.TestApplication.class
)
class PkbItemQueryControllerSecurityTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final PkbItemRecord ITEM = TestPkbItems.itemForUser(USER_ID);

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/actuator/health")).GET().build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void openApiEndpointRemainsPublic() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body())
                .contains("\"openapi\"", "\"/api/pkb/items\"", "\"bearerAuth\"");
    }

    @Test
    void swaggerUiEndpointRemainsPublic() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/swagger-ui/index.html")).GET().build());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains("Swagger UI");
    }

    @Test
    void queryEndpointRequiresBearerToken() throws Exception {
        var response = getItem(null, USER_ID);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void queryEndpointRejectsInactiveBearerToken() throws Exception {
        var response = getItem("inactive", USER_ID);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void queryEndpointAllowsMatchingBioCompassUserScope() throws Exception {
        var response = getItem("valid-user", USER_ID);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains(ITEM.itemId().toString(), USER_ID.toString());
    }

    @Test
    void queryEndpointDeniesDifferentBioCompassUserScope() throws Exception {
        var response = getItem("valid-user", OTHER_USER_ID);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void queryEndpointAllowsStaffCrossUserScope() throws Exception {
        var response = getItem("valid-staff", OTHER_USER_ID);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains(ITEM.itemId().toString(), USER_ID.toString());
    }

    private HttpResponse<String> getItem(String token, UUID requestedUserId) throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/pkb/items/" + ITEM.itemId() + "?userId=" + requestedUserId));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return send(request.GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
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
            BioCompassActorAuthenticationResolver.class,
            OwnerScopedPkbQueryPolicy.class,
            UserIdArgumentResolver.class,
            PkbQueryWebConfig.class,
            PkbItemQueryService.class,
            PkbItemQueryController.class,
            PkbItemQueryExceptionHandler.class
    })
    @ComponentScan(
            basePackageClasses = {
                    BioCompassActorMapper.class,
                    PkbItemResponseMapper.class
            },
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*MapperImpl")
    )
    static class TestApplication {

        @Bean
        OpaqueTokenIntrospector opaqueTokenIntrospector() {
            return token -> switch (token) {
                case "valid-user" -> principal(USER_ID, false);
                case "valid-staff" -> principal(UUID.fromString("33333333-3333-3333-3333-333333333333"), true);
                default -> throw new BadOpaqueTokenException("Inactive BioCompass access token");
            };
        }

        @Bean
        PkbItemQueryRepository pkbItemQueryRepository() {
            return new PkbItemQueryRepository() {
                @Override
                public Optional<PkbItemRecord> findByUserIdAndItemId(UUID userId, UUID itemId) {
                    if (ITEM.itemId().equals(itemId)) {
                        return Optional.of(ITEM);
                    }
                    return Optional.empty();
                }

                @Override
                public List<PkbItemRecord> search(PkbItemSearchCriteria criteria) {
                    return List.of(ITEM);
                }
            };
        }

        private static DefaultOAuth2AuthenticatedPrincipal principal(UUID userId, boolean staff) {
            return new DefaultOAuth2AuthenticatedPrincipal(
                    userId.toString(),
                    Map.of(
                            "user_id", userId.toString(),
                            "email", "user@example.com",
                            "email_verified", true,
                            "is_staff", staff,
                            "roles", staff ? List.of("staff") : List.of("user")
                    ),
                    List.of(new SimpleGrantedAuthority(staff ? "ROLE_staff" : "ROLE_user"))
            );
        }
    }
}
