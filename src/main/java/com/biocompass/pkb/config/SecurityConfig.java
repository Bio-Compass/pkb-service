package com.biocompass.pkb.config;

import com.biocompass.pkb.auth.PkbAuthProperties;
import com.biocompass.pkb.auth.PkbLegacyServiceFilter;
import com.biocompass.pkb.auth.PkbLegacyServiceProperties;
import com.biocompass.pkb.authorization.PkbAuthorizationProperties;
import com.biocompass.pkb.records.PkbRecordProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({PkbLegacyServiceProperties.class, PkbRecordProperties.class,
        PkbAuthProperties.class, PkbAuthorizationProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final OpaqueTokenIntrospector opaqueTokenIntrospector;

    @Bean @Order(2)
    SecurityFilterChain operationalSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/health", "/actuator/health/**", "/actuator/info",
                        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
    }

    @Bean @Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http, PkbLegacyServiceProperties legacy,
            PkbRecordProperties records, PkbAuthProperties auth, PkbAuthorizationProperties authorization) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new PkbLegacyServiceFilter(legacy, records, auth, authorization),
                        BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector)))
                .build();
    }
}
