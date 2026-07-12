package com.biocompass.pkb.query;

import com.biocompass.pkb.security.BioCompassActor;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OwnerScopedPkbQueryPolicy implements PkbQueryPolicy {

    private static final Set<String> CROSS_USER_READ_ROLES = Set.of("pkb:read:any", "pkb_admin");

    @Override
    public void authorizeUserScope(BioCompassActor actor, UUID userId) {
        if (!actor.userId().equals(userId) && !actor.staff() && !actor.hasAnyRole(CROSS_USER_READ_ROLES)) {
            throw new PkbAccessDeniedException("Actor is not authorized for the requested PKB user scope.");
        }
    }
}
