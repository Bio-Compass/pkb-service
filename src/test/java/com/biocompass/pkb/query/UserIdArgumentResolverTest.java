package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import com.biocompass.pkb.security.BioCompassActorMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

@SpringJUnitConfig(UserIdArgumentResolverTest.TestApplication.class)
class UserIdArgumentResolverTest {

    @Autowired
    private UserIdArgumentResolver resolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsUserIdUuidParameters() throws Exception {
        assertThat(resolver.supportsParameter(userIdParameter())).isTrue();
        assertThat(resolver.supportsParameter(unannotatedUuidParameter())).isFalse();
    }

    @Test
    void resolvesAndAuthorizesMatchingUserScope() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authentication(userId));
        MockHttpServletRequest request = request(userId);

        Object resolved = resolver.resolveArgument(userIdParameter(), null, new ServletWebRequest(request), null);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void resolvesCrossUserScopeForAllowedRole() throws Exception {
        UUID actorUserId = UUID.randomUUID();
        UUID requestedUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authentication(actorUserId, "pkb:read:any"));
        MockHttpServletRequest request = request(requestedUserId);

        Object resolved = resolver.resolveArgument(userIdParameter(), null, new ServletWebRequest(request), null);

        assertThat(resolved).isEqualTo(requestedUserId);
    }

    @Test
    void rejectsHeaderIdentityWhenRequestIsUnauthenticated() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request(userId);
        request.addHeader("X-BioCompass-User-Id", userId.toString());

        assertThatThrownBy(() -> resolver.resolveArgument(userIdParameter(), null, new ServletWebRequest(request), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsHeaderIdentityForAuthenticatedNonBioCompassPrincipal() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "authenticated-user",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MockHttpServletRequest request = request(userId);
        request.addHeader("X-BioCompass-User-Id", userId.toString());

        assertThatThrownBy(() -> resolver.resolveArgument(userIdParameter(), null, new ServletWebRequest(request), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsDifferentUserScopeWithoutAllowedRole() {
        SecurityContextHolder.getContext().setAuthentication(authentication(UUID.randomUUID()));
        MockHttpServletRequest request = request(UUID.randomUUID());

        assertThatThrownBy(() -> resolver.resolveArgument(userIdParameter(), null, new ServletWebRequest(request), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsMissingUserIdParameter() {
        SecurityContextHolder.getContext().setAuthentication(authentication(UUID.randomUUID()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pkb/items");

        assertThatThrownBy(() -> resolver.resolveArgument(userIdParameter(), null, new ServletWebRequest(request), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static MockHttpServletRequest request(UUID requestedUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pkb/items");
        request.setParameter("userId", requestedUserId.toString());
        return request;
    }

    private static BearerTokenAuthentication authentication(UUID userId) {
        return authentication(userId, null);
    }

    private static BearerTokenAuthentication authentication(UUID userId, String role) {
        List<String> roles = role == null ? List.of("user") : List.of(role);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2AuthenticatedPrincipal(
                userId.toString(),
                Map.of(
                        "user_id", userId.toString(),
                        "email", "user@example.com",
                        "email_verified", true,
                        "is_staff", false,
                        "roles", roles
                ),
                authorities
        );
        var token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
        return new BearerTokenAuthentication(principal, token, authorities);
    }

    private static MethodParameter userIdParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("annotated", UUID.class);
        return new MethodParameter(method, 0);
    }

    private static MethodParameter unannotatedUuidParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("unannotated", UUID.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static final class TestController {

        void annotated(@UserId UUID userId) {}

        void unannotated(UUID userId) {}
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            BioCompassActorAuthenticationResolver.class,
            OwnerScopedPkbQueryPolicy.class,
            UserIdArgumentResolver.class
    })
    @ComponentScan(
            basePackageClasses = BioCompassActorMapper.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*MapperImpl")
    )
    static class TestApplication {}
}
