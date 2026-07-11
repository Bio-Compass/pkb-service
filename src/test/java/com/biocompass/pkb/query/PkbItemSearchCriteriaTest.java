package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PkbItemSearchCriteriaTest {

    @Test
    void normalizesBlankFilters() {
        UUID userId = UUID.randomUUID();

        PkbItemSearchCriteria criteria = new PkbItemSearchCriteria(
                userId,
                " observation ",
                " ",
                "",
                null,
                null,
                null,
                " medical ",
                " hydration ",
                50,
                0);

        assertThat(criteria.entityType()).isEqualTo("observation");
        assertThat(criteria.subtype()).isNull();
        assertThat(criteria.status()).isNull();
        assertThat(criteria.privacyScope()).isEqualTo("medical");
        assertThat(criteria.textQuery()).isEqualTo("hydration");
    }

    @Test
    void rejectsInvalidObservedWindow() {
        OffsetDateTime observedFrom = OffsetDateTime.parse("2026-07-04T10:00:00Z");
        OffsetDateTime observedUntil = OffsetDateTime.parse("2026-07-04T09:00:00Z");

        assertThatThrownBy(() -> new PkbItemSearchCriteria(
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        observedFrom,
                        observedUntil,
                        null,
                        null,
                        null,
                        50,
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observedUntil");
    }

    @Test
    void rejectsInvalidPaging() {
        assertThatThrownBy(() -> new PkbItemSearchCriteria(
                        UUID.randomUUID(), null, null, null, null, null, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        assertThatThrownBy(() -> new PkbItemSearchCriteria(
                        UUID.randomUUID(), null, null, null, null, null, null, null, null, 50, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }
}
