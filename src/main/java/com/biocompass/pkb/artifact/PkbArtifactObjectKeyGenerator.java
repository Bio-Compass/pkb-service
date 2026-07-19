package com.biocompass.pkb.artifact;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class PkbArtifactObjectKeyGenerator {

    private static final Pattern OBJECT_NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    public String documentObjectKey(UUID userId, UUID documentId, String objectName) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        if (objectName == null || !OBJECT_NAME_PATTERN.matcher(objectName).matches()) {
            throw new IllegalArgumentException("objectName must be a lowercase file name without path separators");
        }
        return "users/%s/documents/%s/%s".formatted(userId, documentId, objectName);
    }
}
