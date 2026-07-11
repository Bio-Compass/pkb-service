package com.biocompass.pkb.query;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record PkbItemSearchCriteria(
        UUID userId,
        String entityType,
        String subtype,
        String status,
        OffsetDateTime observedFrom,
        OffsetDateTime observedUntil,
        OffsetDateTime validAt,
        String privacyScope,
        String textQuery,
        int limit,
        int offset) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public PkbItemSearchCriteria {
        Objects.requireNonNull(userId, "userId must not be null");
        entityType = normalizeBlank(entityType);
        subtype = normalizeBlank(subtype);
        status = normalizeBlank(status);
        privacyScope = normalizeBlank(privacyScope);
        textQuery = normalizeBlank(textQuery);

        if (observedFrom != null && observedUntil != null && observedUntil.isBefore(observedFrom)) {
            throw new IllegalArgumentException("observedUntil must not be before observedFrom");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }

    public static PkbItemSearchCriteria forUser(UUID userId) {
        return new PkbItemSearchCriteria(
                userId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                DEFAULT_LIMIT,
                0);
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }

        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
