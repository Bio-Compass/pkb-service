package com.biocompass.pkb.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(BioCompassActorMapperTest.TestApplication.class)
class BioCompassActorMapperTest {

    @Autowired
    private BioCompassActorMapper mapper;

    @Test
    void mapsBioCompassIntrospectionPrincipalToActor() {
        UUID userId = UUID.randomUUID();
        var principal = new DefaultOAuth2AuthenticatedPrincipal(
                userId.toString(),
                Map.of(
                        "user_id", userId.toString(),
                        "email", "user@example.com",
                        "email_verified", true,
                        "is_staff", true,
                        "roles", List.of("staff")
                ),
                List.of()
        );

        var actor = mapper.toActor(principal);

        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.email()).isEqualTo("user@example.com");
        assertThat(actor.emailVerified()).isTrue();
        assertThat(actor.staff()).isTrue();
        assertThat(actor.roles()).containsExactly("staff");
    }

    @Test
    void rejectsMissingUuidIdentity() {
        var principal = new DefaultOAuth2AuthenticatedPrincipal(
                "not-a-uuid",
                Map.of("sub", "not-a-uuid"),
                List.of()
        );

        assertThatThrownBy(() -> mapper.toActor(principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID user_id");
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = BioCompassActorMapper.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*MapperImpl")
    )
    static class TestApplication {}
}
