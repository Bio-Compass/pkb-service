package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biocompass.pkb.security.BioCompassActor;
import com.biocompass.pkb.security.BioCompassActorAuthenticationResolver;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class UserIdArgumentResolverTest {

    private final UserIdArgumentResolver resolver = new UserIdArgumentResolver(
            new BioCompassActorAuthenticationResolver(),
            new OwnerScopedPkbQueryPolicy());

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

    private static UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return authentication(userId, null);
    }

    private static UsernamePasswordAuthenticationToken authentication(UUID userId, String role) {
        Set<String> roles = role == null ? Set.of() : Set.of(role);
        return UsernamePasswordAuthenticationToken.authenticated(
                new BioCompassActor(userId, roles),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
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
}
