package com.biocompass.pkb.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TestPkbItems {

    private TestPkbItems() {}

    static PkbItemRecord itemForUser(UUID userId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T10:00:00Z");
        return new PkbItemRecord(
                UUID.randomUUID(),
                userId,
                "observation",
                "water",
                "active",
                Map.of("amount", 350, "unit", "ml"),
                "manual",
                "source-1",
                now,
                now,
                now,
                now,
                "en",
                List.of("self"),
                List.of("nutrition", "health"),
                "user_reported",
                null,
                null,
                now,
                now);
    }
}
