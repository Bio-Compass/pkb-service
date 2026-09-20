package com.biocompass.pkb.security;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface BioCompassActorMapper {

    @Mapping(target = "userId", source = "principal", qualifiedByName = "userId")
    @Mapping(target = "actorId", source = "principal", qualifiedByName = "actorId")
    @Mapping(target = "email", source = "principal", qualifiedByName = "email")
    @Mapping(target = "emailVerified", source = "principal", qualifiedByName = "emailVerified")
    @Mapping(target = "staff", source = "principal", qualifiedByName = "staff")
    @Mapping(target = "roles", source = "principal", qualifiedByName = "roles")
    @Mapping(target = "scopes", source = "principal", qualifiedByName = "scopes")
    @Mapping(target = "purposeOfUse", source = "principal", qualifiedByName = "purposeOfUse")
    BioCompassActor toActor(OAuth2AuthenticatedPrincipal principal);

    @Named("userId")
    default UUID userId(OAuth2AuthenticatedPrincipal principal) {
        var userId = stringAttribute(principal, "user_id", principal.getName());
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("BioCompass introspection response does not contain a UUID user_id.", exception);
        }
    }

    @Named("actorId")
    default String actorId(OAuth2AuthenticatedPrincipal principal) {
        return stringAttribute(principal, "actor_id", userId(principal).toString());
    }

    @Named("email")
    default String email(OAuth2AuthenticatedPrincipal principal) {
        return stringAttribute(principal, "email", null);
    }

    @Named("emailVerified")
    default boolean emailVerified(OAuth2AuthenticatedPrincipal principal) {
        return booleanAttribute(principal, "email_verified");
    }

    @Named("staff")
    default boolean staff(OAuth2AuthenticatedPrincipal principal) {
        return booleanAttribute(principal, "is_staff");
    }

    @Named("roles")
    default Set<String> roles(OAuth2AuthenticatedPrincipal principal) {
        var defaultRoles = staff(principal) ? List.of("staff") : List.of("user");
        return values(principal.getAttribute("roles"), defaultRoles);
    }

    @Named("scopes")
    default Set<String> scopes(OAuth2AuthenticatedPrincipal principal) {
        return values(principal.getAttribute("scopes"), List.of());
    }

    @Named("purposeOfUse")
    default String purposeOfUse(OAuth2AuthenticatedPrincipal principal) {
        return stringAttribute(principal, "purpose_of_use", "self");
    }

    private static String stringAttribute(
            OAuth2AuthenticatedPrincipal principal,
            String attributeName,
            String defaultValue
    ) {
        var value = principal.getAttribute(attributeName);
        return value instanceof String text && StringUtils.hasText(text) ? text.strip() : defaultValue;
    }

    private static boolean booleanAttribute(OAuth2AuthenticatedPrincipal principal, String attributeName) {
        return Boolean.TRUE.equals(principal.getAttribute(attributeName));
    }

    private static Set<String> values(Object attribute, Collection<String> defaultValues) {
        if (attribute instanceof String text && StringUtils.hasText(text)) {
            return split(text);
        }
        if (attribute instanceof Collection<?> collection) {
            var values = new LinkedHashSet<String>();
            collection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::strip)
                    .filter(StringUtils::hasText)
                    .forEach(values::add);
            return values.isEmpty() ? Set.copyOf(defaultValues) : Set.copyOf(values);
        }
        return Set.copyOf(defaultValues);
    }

    private static Set<String> split(String text) {
        var values = new LinkedHashSet<String>();
        for (String value : text.split("[\\s,]+")) {
            if (StringUtils.hasText(value)) {
                values.add(value.strip());
            }
        }
        return Set.copyOf(values);
    }
}
