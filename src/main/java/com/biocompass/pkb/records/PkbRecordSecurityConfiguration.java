package com.biocompass.pkb.records;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class PkbRecordSecurityConfiguration {
    @Bean @Order(1)
    SecurityFilterChain recordSecurity(HttpSecurity http, PkbRecordAuthorityClient client) throws Exception {
        return http.securityMatcher("/api/v1/internal/items", "/api/v1/internal/items/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new AuthorityFilter(client), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
    }
    static class AuthorityFilter extends OncePerRequestFilter {
        private final PkbRecordAuthorityClient client;
        AuthorityFilter(PkbRecordAuthorityClient client) { this.client = client; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            response.setHeader("Cache-Control", "no-store");
            try {
                var authority = client.authenticate(request.getHeader("X-BioCompass-Service"),
                        request.getHeader("Authorization"), request.getHeader("X-BioCompass-User-Token"));
                if (request.getParameterMap().containsKey("user_id")) throw new PkbRecordFailure(422, "invalid_request");
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(authority, null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (PkbRecordFailure failure) {
                response.setStatus(failure.status()); response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"" + failure.code() + "\",\"detail\":\"Request unavailable.\"}");
                return;
            }
            try { chain.doFilter(request, response); }
            finally { SecurityContextHolder.clearContext(); }
        }
    }
}
