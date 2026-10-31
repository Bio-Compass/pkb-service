package com.biocompass.pkb.records;

import java.util.Set;
import java.util.UUID;

public record PkbRecordAuthority(UUID userId, String actorId, String producer,
        Set<String> permissions, String purposeOfUse) {
    public PkbRecordAuthority { permissions = Set.copyOf(permissions); }
}
