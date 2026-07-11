package com.biocompass.pkb.security;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BioCompassActor(UUID userId, Set<String> roles) {

    public BioCompassActor {
        Objects.requireNonNull(userId, "userId must not be null");
        roles = normalizeRoles(roles);
    }

    public boolean hasAnyRole(Set<String> expectedRoles) {
        return roles.stream().anyMatch(expectedRoles::contains);
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        Set<String> normalizedRoles = new LinkedHashSet<>();
        roles.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(role -> !role.isEmpty())
                .forEach(normalizedRoles::add);
        return Set.copyOf(normalizedRoles);
    }
}
