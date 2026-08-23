package com.biocompass.pkb.security;

import java.util.Set;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-23T21:46:29+0200",
    comments = "version: 1.6.3, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class BioCompassActorMapperImpl implements BioCompassActorMapper {

    @Override
    public BioCompassActor toActor(OAuth2AuthenticatedPrincipal principal) {
        if ( principal == null ) {
            return null;
        }

        UUID userId = null;
        String email = null;
        boolean emailVerified = false;
        boolean staff = false;
        Set<String> roles = null;

        userId = userId( principal );
        email = email( principal );
        emailVerified = emailVerified( principal );
        staff = staff( principal );
        roles = roles( principal );

        BioCompassActor bioCompassActor = new BioCompassActor( userId, email, emailVerified, staff, roles );

        return bioCompassActor;
    }
}
