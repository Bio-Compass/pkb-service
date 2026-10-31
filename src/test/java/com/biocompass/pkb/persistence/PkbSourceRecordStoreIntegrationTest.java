package com.biocompass.pkb.persistence;

import com.biocompass.pkb.records.persistence.PkbQuantityWindow;
import com.biocompass.pkb.records.persistence.PkbSourceRecord;
import com.biocompass.pkb.records.persistence.PkbSourceRecordConflict;
import com.biocompass.pkb.records.persistence.PkbSourceRecordCreate;
import com.biocompass.pkb.records.persistence.PkbSourceRecordCursor;
import com.biocompass.pkb.records.persistence.PkbSourceRecordStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class PkbSourceRecordStoreIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String PRODUCER = "record-test-producer";
    private static final Instant START = Instant.parse("2026-03-29T00:00:00Z");

    @Autowired private PkbSourceRecordStore store;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void commitsExactCanonicalQuantityProvenanceKeyAndOutboxTogether() {
        var input = input(UUID.randomUUID(), "250.12345678901234567890123456789", START, START.plusSeconds(10));
        var result = store.create(input);
        assertThat(result.outcome()).isEqualTo("created");
        assertThat(result.item().value()).isEqualByComparingTo(input.value());
        assertThat(result.item().recordedAt()).isEqualTo(input.recordedAt());
        assertThat(jdbc.sql("SELECT jsonb_typeof(payload -> 'amount') FROM pkb_item WHERE pkb_item_id = :id")
                .param("id", input.itemId()).query(String.class).single()).isEqualTo("number");
        assertThat(jdbc.sql("SELECT ingested_at > recorded_at FROM pkb_item WHERE pkb_item_id = :id")
                .param("id", input.itemId()).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT source_reference FROM pkb_provenance WHERE pkb_item_id = :id")
                .param("id", input.itemId()).query(String.class).single())
                .isEqualTo(PRODUCER + ":" + input.sourceRecordId());
        assertThat(jdbc.sql("SELECT workflow_id FROM pkb_provenance WHERE pkb_item_id = :id")
                .param("id", input.itemId()).query(String.class).single()).isEqualTo("decision-reference");
        assertThat(count("pkb_item_idempotency", "pkb_item_id", input.itemId())).isEqualTo(1);
        assertThat(count("pkb_outbox_event", "pkb_item_id", input.itemId())).isEqualTo(1);
    }

    @Test
    void rollsBackCanonicalRecordAndAllDependenciesWhenOuterTransactionFails() {
        var input = input(UUID.randomUUID(), "250", START, START);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            store.create(input);
            status.setRollbackOnly();
        });
        assertThat(store.findIncludingDeleted(input.userId(), PRODUCER, input.itemId())).isEmpty();
        assertThat(count("pkb_provenance", "pkb_item_id", input.itemId())).isZero();
        assertThat(count("pkb_item_idempotency", "pkb_item_id", input.itemId())).isZero();
        assertThat(count("pkb_outbox_event", "pkb_item_id", input.itemId())).isZero();
    }

    @Test
    void replaysOriginalCandidateWithoutChangingTimestampsOrEmittingAnotherEvent() {
        var first = input(UUID.randomUUID(), "250", START, START);
        var original = store.create(first).item();
        var replacement = retry(first, "250.0", List.of("health", "nutrition"));
        var replay = store.create(replacement);
        assertThat(replay.outcome()).isEqualTo("replayed");
        assertThat(replay.item()).isEqualTo(original);
        assertThat(count("pkb_outbox_event", "pkb_item_id", first.itemId())).isEqualTo(1);
        assertThat(count("pkb_item", "pkb_item_id", replacement.itemId())).isZero();
    }

    @Test
    void conflictsOnChangedCallerFieldsEvenWhenAssertedFingerprintMatches() {
        var first = input(UUID.randomUUID(), "250", START, START);
        store.create(first);
        assertConflict("idempotency_conflict", () -> store.create(retry(first, "350", first.privacyScope())));
        assertConflict("idempotency_conflict", () -> store.create(retry(first, "250", List.of("public"))));
        var changedHash = new PkbSourceRecordCreate(UUID.randomUUID(), first.userId(), first.producer(),
                UUID.randomUUID().toString(), first.idempotencyKey(), "b".repeat(64), first.entityType(), first.subtype(),
                first.value(), first.unit(), first.observedAt(), first.recordedAt(), first.sourceType(),
                first.privacyScope(), first.consentScope(), first.workflowId());
        assertConflict("idempotency_conflict", () -> store.create(changedHash));
    }

    @Test
    void concurrentEquivalentRequestsCreateOneCanonicalItemAndOneOutboxEvent() throws Exception {
        var first = input(UUID.randomUUID(), "250", START, START);
        var second = retry(first, "250", first.privacyScope());
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> { gate.await(); return store.create(first); });
            var right = executor.submit(() -> { gate.await(); return store.create(second); });
            gate.countDown();
            var a = left.get(20, TimeUnit.SECONDS);
            var b = right.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.outcome(), b.outcome())).containsExactlyInAnyOrder("created", "replayed");
            assertThat(a.item()).isEqualTo(b.item());
            assertThat(count("pkb_outbox_event", "pkb_item_id", a.item().itemId())).isEqualTo(1);
        }
    }

    @Test
    void concurrentDifferentPayloadsHaveOneWinnerAndOneConflict() throws Exception {
        var first = input(UUID.randomUUID(), "250", START, START);
        var second = retry(first, "350", first.privacyScope());
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> { gate.await(); return outcome(first); });
            var right = executor.submit(() -> { gate.await(); return outcome(second); });
            gate.countDown();
            assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "idempotency_conflict");
        }
    }

    @Test
    void sourceAndOwnerConstraintsRejectCollisionsWithoutLeavingOrphans() {
        var first = input(UUID.randomUUID(), "250", START, START);
        store.create(first);
        var collision = new PkbSourceRecordCreate(UUID.randomUUID(), UUID.randomUUID(), first.producer(),
                first.sourceRecordId(), UUID.randomUUID(), first.fingerprint(), first.entityType(), first.subtype(),
                first.value(), first.unit(), first.observedAt(), first.recordedAt(), first.sourceType(),
                first.privacyScope(), first.consentScope(), first.workflowId());
        assertConflict("source_conflict", () -> store.create(collision));
        assertThat(count("pkb_item", "pkb_item_id", collision.itemId())).isZero();
        assertThatThrownBy(() -> jdbc.sql("UPDATE pkb_item_idempotency SET user_id = :user WHERE pkb_item_id = :id")
                .param("user", UUID.randomUUID()).param("id", first.itemId()).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_pkb_item_idempotency_owner");
    }

    @Test
    void lookupHistoryTotalsAndRecentNeverExposeOtherOwnersProducersOrDeletedItems() {
        var user = UUID.randomUUID();
        var first = store.create(input(user, "250", START, START)).item();
        var later = store.create(input(user, "300", START.plusSeconds(1), START.plusSeconds(1))).item();
        var deleted = store.create(input(user, "900", START.plusSeconds(2), START.plusSeconds(2))).item();
        store.delete(user, PRODUCER, deleted.itemId());
        store.create(input(UUID.randomUUID(), "999", START, START));
        var another = input(user, "888", START, START);
        store.create(new PkbSourceRecordCreate(another.itemId(), user, "other-producer", another.sourceRecordId(),
                another.idempotencyKey(), another.fingerprint(), another.entityType(), another.subtype(), another.value(),
                another.unit(), another.observedAt(), another.recordedAt(), another.sourceType(),
                another.privacyScope(), another.consentScope(), another.workflowId()));
        assertThat(store.find(UUID.randomUUID(), PRODUCER, first.itemId())).isEmpty();
        assertThat(store.find(user, "other-producer", first.itemId())).isEmpty();
        assertThat(store.find(user, PRODUCER, deleted.itemId())).isEmpty();
        assertThat(history(user, null, 500)).extracting(PkbSourceRecord::itemId)
                .containsExactly(first.itemId(), later.itemId());
        assertThat(store.totals(user, PRODUCER, "nutrition_intake", "water", "mL",
                List.of(new PkbQuantityWindow(START, START.plusSeconds(3)))))
                .containsExactly(new BigDecimal("550"));
        assertThat(store.recentQuantities(user, PRODUCER, "nutrition_intake", "water", "mL", 3))
                .containsExactly(new BigDecimal("300"), new BigDecimal("250"));
    }

    @Test
    void keysetHistoryHandlesTimestampTiesWithoutSkippingOrDuplicatingRecords() {
        var user = UUID.randomUUID();
        for (int i = 0; i < 5; i++) store.create(input(user, "250", START, START));
        var complete = history(user, null, 500);
        var first = history(user, null, 2);
        var second = history(user, cursor(first.getLast()), 2);
        var third = history(user, cursor(second.getLast()), 2);
        assertThat(java.util.stream.Stream.of(first, second, third).flatMap(List::stream).toList()).isEqualTo(complete);
    }

    @Test
    void totalsUseHalfOpenWindowsExactArithmeticAndUnboundedSumPrecision() {
        var user = UUID.randomUUID();
        store.create(input(user, "2147483647", START, START));
        store.create(input(user, "2147483647", START.plusSeconds(1), START));
        store.create(input(user, "0.1234567890123456789", START.plusSeconds(2), START));
        var totals = store.totals(user, PRODUCER, "nutrition_intake", "water", "mL", List.of(
                new PkbQuantityWindow(START, START.plusSeconds(2)),
                new PkbQuantityWindow(START.plusSeconds(2), START.plusSeconds(3)),
                new PkbQuantityWindow(START.plusSeconds(3), START.plusSeconds(4))));
        assertThat(totals.get(0)).isEqualByComparingTo("4294967294");
        assertThat(totals.get(1)).isEqualByComparingTo("0.1234567890123456789");
        assertThat(totals.get(2)).isEqualByComparingTo("0");
        assertThat(store.totals(user, PRODUCER, "nutrition_intake", "water", "mL", List.of())).isEmpty();
        assertThatThrownBy(() -> store.totals(user, PRODUCER, "nutrition_intake", "water", "mL",
                java.util.Collections.nCopies(31, new PkbQuantityWindow(START, START.plusSeconds(1)))))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void recentDistinctQuantitiesUseObservedThenRecordedThenAmountTieBreaks() {
        var user = UUID.randomUUID();
        store.create(input(user, "500", START, START));
        store.create(input(user, "250", START, START.plusSeconds(1)));
        store.create(input(user, "300", START, START.plusSeconds(1)));
        store.create(input(user, "250", START, START));
        assertThat(store.recentQuantities(user, PRODUCER, "nutrition_intake", "water", "mL", 3))
                .containsExactly(new BigDecimal("300"), new BigDecimal("250"), new BigDecimal("500"));
    }

    @Test
    void deleteDoesNotEnumerateForeignRecordsAndRetiresKeyUntilExplicitPurge() {
        var input = input(UUID.randomUUID(), "250", START, START);
        store.create(input);
        store.delete(UUID.randomUUID(), PRODUCER, input.itemId());
        store.delete(input.userId(), "other-producer", input.itemId());
        assertThat(store.find(input.userId(), PRODUCER, input.itemId())).isPresent();
        store.delete(input.userId(), PRODUCER, UUID.randomUUID());
        store.delete(input.userId(), PRODUCER, input.itemId());
        store.delete(input.userId(), PRODUCER, input.itemId());
        assertThat(store.findIncludingDeleted(input.userId(), PRODUCER, input.itemId())).isPresent();
        assertConflict("key_retired", () -> store.create(retry(input, "250", input.privacyScope())));
        assertThat(store.purgeDeleted(input.userId(), PRODUCER, Instant.now().minusSeconds(90L * 86400), 100)).isZero();
        assertThat(store.purgeDeleted(input.userId(), PRODUCER, Instant.now().plusSeconds(1), 100)).isEqualTo(1);
        assertThat(count("pkb_item_idempotency", "pkb_item_id", input.itemId())).isZero();
        assertThat(count("pkb_provenance", "pkb_item_id", input.itemId())).isZero();
        assertThat(count("pkb_outbox_event", "pkb_item_id", input.itemId())).isZero();
        assertThat(store.create(retry(input, "250", input.privacyScope())).outcome()).isEqualTo("created");
    }

    @Test
    void purgeIsBoundedAndRejectsRecordsReferencedByOtherCanonicalData() {
        var user = UUID.randomUUID();
        var a = store.create(input(user, "250", START, START)).item();
        var b = store.create(input(user, "300", START, START)).item();
        store.delete(user, PRODUCER, a.itemId());
        store.delete(user, PRODUCER, b.itemId());
        assertThat(store.purgeDeleted(user, PRODUCER, Instant.now().plusSeconds(1), 1)).isEqualTo(1);
        var retained = store.findIncludingDeleted(user, PRODUCER, a.itemId()).orElseGet(
                () -> store.findIncludingDeleted(user, PRODUCER, b.itemId()).orElseThrow());
        jdbc.sql("INSERT INTO pkb_artifact(artifact_id,user_id,pkb_item_id,object_key) VALUES (:id,:user,:item,:key)")
                .param("id", UUID.randomUUID()).param("user", user).param("item", retained.itemId())
                .param("key", UUID.randomUUID().toString()).update();
        assertConflict("purge_in_use", () -> store.purgeDeleted(user, PRODUCER, Instant.now().plusSeconds(1), 10));
        assertThat(store.findIncludingDeleted(user, PRODUCER, retained.itemId())).isPresent();
        assertThat(count("pkb_item_idempotency", "pkb_item_id", retained.itemId())).isEqualTo(1);
    }

    @Test
    void databaseRejectsInvalidSourcePairMissingUnitAndInconsistentDeletedState() {
        var input = input(UUID.randomUUID(), "250", START, START);
        store.create(input);
        assertThatThrownBy(() -> jdbc.sql("UPDATE pkb_item SET source_record_id = NULL WHERE pkb_item_id = :id")
                .param("id", input.itemId()).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE pkb_item SET payload = '{\"amount\":250}'::jsonb WHERE pkb_item_id = :id")
                .param("id", input.itemId()).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE pkb_item SET status = 'deleted' WHERE pkb_item_id = :id")
                .param("id", input.itemId()).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void governanceGroupsReturnActualActiveMetadataWithinRequestedRange() {
        var user = UUID.randomUUID();
        store.create(input(user, "250", START, START));
        var restricted = input(user, "300", START.plusSeconds(1), START);
        store.create(new PkbSourceRecordCreate(restricted.itemId(), user, PRODUCER, restricted.sourceRecordId(),
                restricted.idempotencyKey(), restricted.fingerprint(), restricted.entityType(), restricted.subtype(),
                restricted.value(), restricted.unit(), restricted.observedAt(), restricted.recordedAt(), "device",
                List.of("restricted"), List.of("explicit-consent"), restricted.workflowId()));
        assertThat(store.governanceGroups(user, PRODUCER, "nutrition_intake", "water", START, START.plusSeconds(1)))
                .singleElement().satisfies(group -> assertThat(group.privacyScope()).containsExactly("nutrition", "health"));
        assertThat(store.governanceGroups(user, PRODUCER, "nutrition_intake", "water", null, null)).hasSize(2);
    }

    @Test
    void governanceGroupLimitIsASafeDependencyFailure() {
        var user = UUID.randomUUID();
        for (int index = 0; index <= 100; index++) {
            var record = input(user, "250", START, START);
            store.create(new PkbSourceRecordCreate(record.itemId(), user, PRODUCER, record.sourceRecordId(),
                    record.idempotencyKey(), record.fingerprint(), record.entityType(), record.subtype(),
                    record.value(), record.unit(), record.observedAt(), record.recordedAt(), record.sourceType(),
                    List.of("privacy-" + index), List.of(), record.workflowId()));
        }
        assertThatThrownBy(() -> store.governanceGroups(user, PRODUCER, "nutrition_intake", "water", null, null))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThatThrownBy(() -> store.governanceGroupsForWindows(user, PRODUCER, "nutrition_intake", "water",
                List.of(new PkbQuantityWindow(START, START.plusSeconds(1)))))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(store.governanceGroupsForWindows(user, PRODUCER, "nutrition_intake", "water", List.of())).isEmpty();
    }

    @Test
    void governanceWindowsExcludeGapsEndpointsOtherOwnersProducersAndDeletedRecords() {
        var user = UUID.randomUUID();
        store.create(input(user, "250", START, START));
        store.create(input(user, "350", START.plusSeconds(4), START));
        for (int second : List.of(1, 2, 5)) {
            store.create(withGovernance(input(user, "900", START.plusSeconds(second), START), user,
                    PRODUCER, List.of("excluded-" + second)));
        }
        var deleted = withGovernance(input(user, "900", START, START), user, PRODUCER, List.of("deleted"));
        store.create(deleted);
        store.delete(user, PRODUCER, deleted.itemId());
        var otherUser = UUID.randomUUID();
        store.create(withGovernance(input(otherUser, "900", START, START), otherUser, PRODUCER, List.of("other-owner")));
        store.create(withGovernance(input(user, "900", START, START), user, "other-producer", List.of("other-producer")));

        var groups = store.governanceGroupsForWindows(user, PRODUCER, "nutrition_intake", "water", List.of(
                new PkbQuantityWindow(START, START.plusSeconds(1)),
                new PkbQuantityWindow(START.plusSeconds(4), START.plusSeconds(5))));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.sourceType()).isEqualTo("self_reported");
            assertThat(group.privacyScope()).containsExactly("nutrition", "health");
            assertThat(group.consentScope()).isEmpty();
        });
    }

    @Test
    void governanceWindowsDeduplicateOverlapsAndRejectUnboundedRequests() {
        var user = UUID.randomUUID();
        store.create(input(user, "250", START, START));
        var window = new PkbQuantityWindow(START, START.plusSeconds(2));
        assertThat(store.governanceGroupsForWindows(user, PRODUCER, "nutrition_intake", "water",
                java.util.Collections.nCopies(30, window))).hasSize(1);
        assertThatThrownBy(() -> store.governanceGroupsForWindows(user, PRODUCER, "nutrition_intake", "water",
                java.util.Collections.nCopies(31, window))).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> store.governanceGroupsForWindows(user, PRODUCER, "nutrition_intake", "water",
                List.of(new PkbQuantityWindow(START, START)))).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    private static PkbSourceRecordCreate withGovernance(PkbSourceRecordCreate input, UUID user,
                                                        String producer, List<String> privacy) {
        return new PkbSourceRecordCreate(input.itemId(), user, producer, input.sourceRecordId(), input.idempotencyKey(),
                input.fingerprint(), input.entityType(), input.subtype(), input.value(), input.unit(), input.observedAt(),
                input.recordedAt(), input.sourceType(), privacy, input.consentScope(), input.workflowId());
    }

    private String outcome(PkbSourceRecordCreate input) {
        try { return store.create(input).outcome(); }
        catch (PkbSourceRecordConflict conflict) { return conflict.code(); }
    }

    private List<PkbSourceRecord> history(UUID user, PkbSourceRecordCursor cursor, int limit) {
        return store.history(user, PRODUCER, "nutrition_intake", "water", START, START.plusSeconds(10), limit, cursor);
    }

    private static PkbSourceRecordCursor cursor(PkbSourceRecord record) {
        return new PkbSourceRecordCursor(record.observedAt(), record.recordedAt(), record.itemId());
    }

    private int count(String table, String column, UUID id) {
        // Both identifiers are compile-time test constants; all record values remain bound parameters.
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :id")
                .param("id", id).query(Integer.class).single();
    }

    private static PkbSourceRecordCreate input(UUID user, String value, Instant observed, Instant recorded) {
        var id = UUID.randomUUID();
        return new PkbSourceRecordCreate(id, user, PRODUCER, id.toString(), UUID.randomUUID(), "a".repeat(64),
                "nutrition_intake", "water", new BigDecimal(value), "mL", observed, recorded, "self_reported",
                List.of("nutrition", "health"), List.of(), "decision-reference");
    }

    private static PkbSourceRecordCreate retry(PkbSourceRecordCreate first, String value, List<String> privacy) {
        var id = UUID.randomUUID();
        return new PkbSourceRecordCreate(id, first.userId(), first.producer(), id.toString(), first.idempotencyKey(),
                first.fingerprint(), first.entityType(), first.subtype(), new BigDecimal(value), first.unit(),
                first.observedAt().plusSeconds(10), first.recordedAt().plusSeconds(10), first.sourceType(),
                privacy, first.consentScope(), first.workflowId());
    }

    private static void assertConflict(String code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(PkbSourceRecordConflict.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
