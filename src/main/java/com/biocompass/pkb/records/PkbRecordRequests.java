package com.biocompass.pkb.records;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PkbRecordRequests {
    private PkbRecordRequests() {}
    public record Quantity(@NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=30, fraction=12) BigDecimal value,
            @NotNull @Pattern(regexp="mL") String unit) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException(); }
    }
    public record Create(@NotNull @JsonProperty("item_id") UUID itemId,
            @NotBlank @Size(max=255) @JsonProperty("source_record_id") String sourceRecordId,
            @NotNull @JsonProperty("idempotency_key") UUID idempotencyKey,
            @NotNull @Pattern(regexp="[0-9a-f]{64}") String fingerprint,
            @NotNull @Pattern(regexp="[a-z][a-z0-9_]{0,63}") @JsonProperty("entity_type") String entityType,
            @NotNull @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String subtype,
            @NotNull @Valid Quantity quantity, @NotNull @JsonProperty("observed_at") Instant observedAt,
            @NotNull @JsonProperty("recorded_at") Instant recordedAt,
            @NotBlank @Size(max=100) @JsonProperty("source_type") String sourceType,
            @NotNull @Size(max=32) @JsonProperty("privacy_scope") List<@NotBlank @Size(max=100) String> privacyScope,
            @NotNull @Size(max=32) @JsonProperty("consent_scope") List<@NotBlank @Size(max=100) String> consentScope) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException(); }
    }
    public record Window(@NotNull Instant from, @NotNull Instant before) {
        @AssertTrue public boolean isOrdered() { return from != null && before != null && from.isBefore(before); }
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException(); }
    }
    public record Totals(@NotNull @Pattern(regexp="[a-z][a-z0-9_]{0,63}") @JsonProperty("entity_type") String entityType,
            @NotNull @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String subtype,
            @NotNull @Pattern(regexp="mL") String unit, @NotNull @Size(max=30) List<@NotNull @Valid Window> windows) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException(); }
    }
}
