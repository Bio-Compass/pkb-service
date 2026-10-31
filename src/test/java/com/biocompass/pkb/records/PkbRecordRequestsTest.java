package com.biocompass.pkb.records;

import com.biocompass.pkb.records.persistence.PkbSourceRecord;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbRecordRequestsTest {
    private static final Instant NOW = Instant.parse("2026-10-24T09:00:00.123456Z");
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void validation() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }

    @Test void acceptsPositiveExactScalarAndDocumentedUnits() {
        assertThat(validator.validate(new PkbRecordRequests.Quantity(new BigDecimal("250.123456789012"), "mL"))).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.0000000000001", "1000000000000000000000000000000"})
    void rejectsNonPositiveOrOutOfBoundDecimals(String value) {
        assertThat(validator.validate(new PkbRecordRequests.Quantity(new BigDecimal(value), "mL"))).isNotEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"ml", "L", "", "g"})
    void requiresTheCurrentExactUnit(String unit) {
        assertThat(validator.validate(new PkbRecordRequests.Quantity(BigDecimal.ONE, unit))).isNotEmpty();
    }

    @Test void rejectsReversedAndEmptyWindowsAndBoundsWindowCount() {
        assertThat(validator.validate(new PkbRecordRequests.Window(NOW, NOW))).isNotEmpty();
        assertThat(validator.validate(new PkbRecordRequests.Window(NOW.plusSeconds(1), NOW))).isNotEmpty();
        var window = new PkbRecordRequests.Window(NOW, NOW.plusSeconds(1));
        assertThat(validator.validate(new PkbRecordRequests.Totals("nutrition_intake", "water", "mL", List.of()))).isEmpty();
        assertThat(validator.validate(new PkbRecordRequests.Totals("nutrition_intake", "water", "mL", Collections.nCopies(30, window)))).isEmpty();
        assertThat(validator.validate(new PkbRecordRequests.Totals("nutrition_intake", "water", "mL", Collections.nCopies(31, window)))).isNotEmpty();
    }

    @Test void itemMappingPreservesPublicIdentityExactValueAndOriginalTimes() {
        var record = record();
        var mapped = PkbRecordController.Item.from(record);
        assertThat(mapped.itemId()).isEqualTo(record.itemId());
        assertThat(mapped.userId()).isEqualTo(record.userId());
        assertThat(mapped.sourceSystem()).isEqualTo(record.sourceSystem());
        assertThat(mapped.sourceRecordId()).isEqualTo(record.sourceRecordId());
        assertThat(mapped.idempotencyKey()).isEqualTo(record.idempotencyKey());
        assertThat(mapped.fingerprint()).isEqualTo(record.fingerprint());
        assertThat(mapped.quantity().value()).isEqualTo("250.123456789012");
        assertThat(mapped.quantity().unit()).isEqualTo("mL");
        assertThat(mapped.observedAt()).isEqualTo(NOW);
        assertThat(mapped.recordedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test void cursorRoundTripPreservesTimestampPrecisionAndUuidTies() {
        var record = record();
        var cursor = PkbRecordController.decodeCursor(PkbRecordController.encodeCursor(record));
        assertThat(cursor.observedAt()).isEqualTo(record.observedAt());
        assertThat(cursor.recordedAt()).isEqualTo(record.recordedAt());
        assertThat(cursor.itemId()).isEqualTo(record.itemId());
        assertThat(PkbRecordController.decodeCursor(null)).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"", "***", "eA", "YmFkLXx0aW1lfGlk"})
    void malformedCursorFailsAsValidation(String cursor) {
        assertThatThrownBy(() -> PkbRecordController.decodeCursor(cursor)).isInstanceOfSatisfying(PkbRecordFailure.class, failure -> {
            assertThat(failure.status()).isEqualTo(422);
            assertThat(failure.code()).isEqualTo("invalid_request");
        });
    }

    @Test void cursorRejectsExtraFields() {
        String cursor = Base64.getUrlEncoder().encodeToString((NOW + "|" + NOW + "|" + UUID.randomUUID() + "|extra")
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> PkbRecordController.decodeCursor(cursor)).isInstanceOf(PkbRecordFailure.class);
    }

    private static PkbSourceRecord record() {
        var id = UUID.randomUUID();
        return new PkbSourceRecord(id, UUID.randomUUID(), "producer-service", id.toString(), UUID.randomUUID(), "a".repeat(64),
                "nutrition_intake", "water", new BigDecimal("250.123456789012"), "mL", NOW, NOW.plusSeconds(1),
                "device", List.of("health"), List.of("self"), null);
    }
}
