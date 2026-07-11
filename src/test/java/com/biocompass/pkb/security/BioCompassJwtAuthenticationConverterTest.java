package com.biocompass.pkb.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class BioCompassJwtAuthenticationConverterTest {

    private final BioCompassJwtAuthenticationConverter converter = new BioCompassJwtAuthenticationConverter();

    @Test
    void convertsJwtClaimsToBioCompassActorPrincipal() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("roles", List.of("pkb_admin"))
                .claim("scope", "pkb:read:any profile")
                .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getPrincipal())
                .isInstanceOf(BioCompassActor.class)
                .satisfies(principal -> {
                    BioCompassActor actor = (BioCompassActor) principal;
                    assertThat(actor.userId()).isEqualTo(userId);
                    assertThat(actor.roles()).contains("pkb_admin", "pkb:read:any", "profile");
                });
    }

    @Test
    void rejectsJwtWithoutUuidUserIdentity() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }
}
