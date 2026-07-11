package com.biocompass.pkb.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BioCompassJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUuid(jwt.getClaimAsString("user_id"))
                .or(() -> parseUuid(jwt.getSubject()))
                .orElseThrow(() -> new IllegalArgumentException("JWT does not contain a UUID BioCompass user identity."));
        return UsernamePasswordAuthenticationToken.authenticated(
                new BioCompassActor(userId, rolesFromJwt(jwt)),
                jwt,
                Set.of());
    }

    private Set<String> rolesFromJwt(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(values(jwt.getClaim("roles")));
        roles.addAll(splitRoles(jwt.getClaimAsString("scope"), " "));
        roles.addAll(values(jwt.getClaim("scp")));
        return roles;
    }

    private Set<String> values(Object value) {
        if (value instanceof Collection<?> collection) {
            Set<String> roles = new LinkedHashSet<>();
            collection.stream()
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .map(String::strip)
                    .forEach(roles::add);
            return roles;
        }
        if (value instanceof String string) {
            return splitRoles(string, ",");
        }
        return Set.of();
    }

    private Set<String> splitRoles(String value, String delimiterRegex) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }

        Set<String> roles = new LinkedHashSet<>();
        Arrays.stream(value.split(delimiterRegex))
                .map(String::strip)
                .filter(StringUtils::hasText)
                .forEach(roles::add);
        return roles;
    }

    private Optional<UUID> parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
