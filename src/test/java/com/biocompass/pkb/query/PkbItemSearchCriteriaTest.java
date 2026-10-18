package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\u2003"})
    void normalizesCanonicalFiltersWhilePreservingTextCase(String whitespace) {
        PkbItemSearchCriteria criteria = new PkbItemSearchCriteria(
                UUID.randomUUID(),
                whitespace + "ObservatIon" + whitespace,
                whitespace + "HydratIon" + whitespace,
                whitespace + "ACTIVE" + whitespace,
                null, null, null,
                whitespace + "Health" + whitespace,
                whitespace + "Mixed CASE text" + whitespace,
                50, 0);

        assertThat(criteria.entityType()).isEqualTo("observation");
        assertThat(criteria.subtype()).isEqualTo("hydration");
        assertThat(criteria.status()).isEqualTo("active");
        assertThat(criteria.privacyScope()).isEqualTo("health");
        assertThat(criteria.textQuery()).isEqualTo("Mixed CASE text");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void treatsAbsentAndBlankFiltersAsUnspecified(String value) {
        PkbItemSearchCriteria criteria = new PkbItemSearchCriteria(
                UUID.randomUUID(), value, value, value, null, null, null, value, value, 50, 0);

        assertThat(criteria.entityType()).isNull();
        assertThat(criteria.subtype()).isNull();
        assertThat(criteria.status()).isNull();
        assertThat(criteria.privacyScope()).isNull();
        assertThat(criteria.textQuery()).isNull();
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void normalizesCanonicalFiltersIndependentlyOfDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            PkbItemSearchCriteria criteria = new PkbItemSearchCriteria(
                    UUID.randomUUID(), "OBSERVATION", "HYDRATION", "ACTIVE",
                    null, null, null, "PRIVATE", "Mixed I", 50, 0);

            assertThat(criteria.entityType()).isEqualTo("observation");
            assertThat(criteria.subtype()).isEqualTo("hydration");
            assertThat(criteria.status()).isEqualTo("active");
            assertThat(criteria.privacyScope()).isEqualTo("private");
            assertThat(criteria.textQuery()).isEqualTo("Mixed I");
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
        }
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
