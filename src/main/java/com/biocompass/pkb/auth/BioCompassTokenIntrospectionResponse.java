package com.biocompass.pkb.auth;

public record BioCompassTokenIntrospectionResponse(
        boolean active,
        String userId,
        String email,
        boolean emailVerified,
        boolean staff
) {

    public static BioCompassTokenIntrospectionResponse inactive() {
        return new BioCompassTokenIntrospectionResponse(false, null, null, false, false);
    }
}
