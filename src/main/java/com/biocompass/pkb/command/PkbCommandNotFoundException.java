package com.biocompass.pkb.command;

import java.util.UUID;

public class PkbCommandNotFoundException extends RuntimeException {

    public PkbCommandNotFoundException(String resourceType, UUID resourceId) {
        super("%s not found: %s".formatted(resourceType, resourceId));
    }
}
