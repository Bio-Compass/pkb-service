package com.biocompass.pkb.query;

import com.biocompass.pkb.security.BioCompassActor;
import java.util.UUID;

public interface PkbQueryPolicy {

    void authorizeUserScope(BioCompassActor actor, UUID userId);
}
