package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biocompass.pkb.security.BioCompassActor;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerScopedPkbQueryPolicyTest {

    private final OwnerScopedPkbQueryPolicy policy = new OwnerScopedPkbQueryPolicy();

    @Test
    void authorizesMatchingUserScope() {
        UUID userId = UUID.randomUUID();

        policy.authorizeUserScope(new BioCompassActor(userId, Set.of()), userId);
    }

    @Test
    void authorizesCrossUserScopeForConfiguredRole() {
        UUID actorUserId = UUID.randomUUID();
        UUID requestedUserId = UUID.randomUUID();

        policy.authorizeUserScope(new BioCompassActor(actorUserId, Set.of("pkb:read:any")), requestedUserId);
    }

    @Test
    void authorizesCrossUserScopeForBioCompassStaff() {
        UUID actorUserId = UUID.randomUUID();
        UUID requestedUserId = UUID.randomUUID();

        policy.authorizeUserScope(new BioCompassActor(actorUserId, null, true, true, Set.of("staff")), requestedUserId);
    }

    @Test
    void deniesDifferentUserScopeWithoutCrossUserRole() {
        UUID actorUserId = UUID.randomUUID();
        UUID requestedUserId = UUID.randomUUID();

        assertThatThrownBy(() -> policy.authorizeUserScope(new BioCompassActor(actorUserId, Set.of()), requestedUserId))
                .isInstanceOf(PkbAccessDeniedException.class)
                .hasMessageContaining("not authorized");
    }
}
