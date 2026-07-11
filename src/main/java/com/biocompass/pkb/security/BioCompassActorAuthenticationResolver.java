package com.biocompass.pkb.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BioCompassActorAuthenticationResolver {

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    public BioCompassActor resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authenticationTrustResolver.isAnonymous(authentication)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated BioCompass identity.");
        }
        if (!(authentication.getPrincipal() instanceof BioCompassActor actor)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated principal is not a BioCompass actor.");
        }
        return actor;
    }
}
