package com.biocompass.pkb.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BioCompassOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final BioCompassTokenIntrospectionClient introspectionClient;
    private final PkbAuthProperties authProperties;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        var response = introspectionClient.introspect(token);
        if (!response.active()) {
            throw new BadOpaqueTokenException("Inactive BioCompass access token");
        }

        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("sub", response.userId());
        attributes.put("actor_id", response.userId());
        attributes.put("user_id", response.userId());
        attributes.put("email", response.email());
        attributes.put("email_verified", response.emailVerified());
        attributes.put("is_staff", response.staff());
        attributes.put("roles", response.staff() ? List.of("staff") : List.of("user"));
        attributes.put("scopes", List.of());
        attributes.put("purpose_of_use", authProperties.defaultPurpose());

        var authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_user"));
        if (response.staff()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_staff"));
        }

        return new DefaultOAuth2AuthenticatedPrincipal(response.userId(), attributes, authorities);
    }
}
