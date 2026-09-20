package com.biocompass.pkb.security;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BioCompassActor(
        UUID userId,
        String actorId,
        String email,
        boolean emailVerified,
        boolean staff,
        Set<String> roles,
        Set<String> scopes,
        String purposeOfUse
) {

    public BioCompassActor {
        Objects.requireNonNull(userId, "userId must not be null");
        actorId = actorId == null || actorId.isBlank() ? userId.toString() : actorId.strip();
        roles = normalizeRoles(roles);
        scopes = normalizeRoles(scopes);
        purposeOfUse = purposeOfUse == null || purposeOfUse.isBlank() ? "self" : purposeOfUse.strip();
    }

    public BioCompassActor(
            UUID userId,
            String email,
            boolean emailVerified,
            boolean staff,
            Set<String> roles
    ) {
        this(userId, null, email, emailVerified, staff, roles, Set.of(), "self");
    }

    public BioCompassActor(UUID userId, Set<String> roles) {
        this(userId, null, null, false, false, roles, Set.of(), "self");
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
