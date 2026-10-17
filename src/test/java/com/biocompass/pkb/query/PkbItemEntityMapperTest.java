package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PkbItemEntityMapperTest {

    private final PkbItemEntityMapper mapper = new PkbItemEntityMapperImpl();

    @Test
    void mapsObservedTimezoneToRecord() {
        var entity = PkbItemEntity.builder()
                .pkbItemId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .entityType("observation")
                .subtype("heart_rate")
                .status("active")
                .payload(new LinkedHashMap<>())
                .sourceType("device")
                .observedAt(Instant.parse("2026-06-20T08:15:00Z"))
                .observedTimezone("Europe/Amsterdam")
                .ingestedAt(Instant.now())
                .consentScope(new String[0])
                .privacyScope(new String[0])
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        var record = mapper.toRecord(entity);

        assertThat(record.observedTimezone()).isEqualTo("Europe/Amsterdam");
    }

    @Test
    void mapsNullObservedTimezoneToRecord() {
        var entity = PkbItemEntity.builder()
                .pkbItemId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .entityType("observation")
                .subtype("heart_rate")
                .status("active")
                .payload(new LinkedHashMap<>())
                .sourceType("device")
                .observedAt(Instant.parse("2026-06-20T08:15:00Z"))
                .ingestedAt(Instant.now())
                .consentScope(new String[0])
                .privacyScope(new String[0])
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        var record = mapper.toRecord(entity);

        assertThat(record.observedTimezone()).isNull();
    }
}
