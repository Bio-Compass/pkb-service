package com.biocompass.pkb.records.persistence;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import com.biocompass.pkb.command.event.PkbDomainEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Producer-scoped operations on canonical facts; acquisition channels remain request metadata. */
@Repository
public class PkbSourceRecordStore {
    private static final String FIELDS = """
            i.pkb_item_id, i.user_id, i.source_system, i.source_record_id,
            k.idempotency_key, k.fingerprint, i.entity_type, i.subtype,
            (i.payload ->> 'amount')::numeric AS amount, i.payload ->> 'unit' AS unit,
            i.observed_at, i.recorded_at, i.source_type, i.privacy_scope, i.consent_scope, i.deleted_at
            """;
    private static final String JOIN = """
             FROM pkb_item i JOIN pkb_item_idempotency k
               ON k.pkb_item_id = i.pkb_item_id AND k.user_id = i.user_id AND k.producer = i.source_system
            """;
    private static final String OWNED = " i.user_id = :user AND i.source_system = :producer ";
    private static final String ACTIVE = " AND i.deleted_at IS NULL AND i.status = 'active' ";
    private static final RowMapper<PkbSourceRecord> RECORD_MAPPER = (rs, row) -> new PkbSourceRecord(
            rs.getObject("pkb_item_id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getString("source_system"), rs.getString("source_record_id"),
            rs.getObject("idempotency_key", UUID.class), rs.getString("fingerprint"),
            rs.getString("entity_type"), rs.getString("subtype"), rs.getBigDecimal("amount"), rs.getString("unit"),
            instant(rs, "observed_at"), instant(rs, "recorded_at"), rs.getString("source_type"),
            strings(rs, "privacy_scope"), strings(rs, "consent_scope"), instant(rs, "deleted_at"));

    private final JdbcClient jdbc;
    private final PkbDomainEventPublisher eventPublisher;

    public PkbSourceRecordStore(JdbcClient jdbc, PkbDomainEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
    }

    /** Canonical facts, provenance, keys and the outbox participate in one Spring transaction. */
    @Transactional
    public PkbSourceRecordWriteResult create(PkbSourceRecordCreate command) {
        // Serialize absent-key contenders too; a row lock alone cannot lock a key that does not exist.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", command.producer() + ":" + command.userId() + ":" + command.idempotencyKey())
                .query((rs, row) -> rs.getString(1)).list();
        var existing = owned(jdbc.sql("SELECT " + FIELDS + JOIN + " WHERE " + OWNED
                        + " AND k.idempotency_key = :key FOR UPDATE OF i"), command.userId(), command.producer())
                .param("key", command.idempotencyKey()).query(RECORD_MAPPER).optional();
        if (existing.isPresent()) {
            var record = existing.get();
            if (record.deletedAt() != null) {
                throw new PkbSourceRecordConflict("key_retired");
            }
            if (!equivalent(record, command)) {
                throw new PkbSourceRecordConflict("idempotency_conflict");
            }
            return new PkbSourceRecordWriteResult("replayed", record);
        }
        int inserted = jdbc.sql("""
                        INSERT INTO pkb_item (pkb_item_id, user_id, entity_type, subtype, status, payload,
                            source_type, source_system, source_record_id, observed_at, recorded_at,
                            privacy_scope, consent_scope)
                        VALUES (:id, :user, :entity, :subtype, 'active',
                            jsonb_build_object('amount', CAST(:amount AS numeric), 'unit', CAST(:unit AS text)),
                            :sourceType, :producer, :sourceRecord, :observed, :recorded, :privacy, :consent)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", command.itemId()).param("user", command.userId())
                .param("entity", command.entityType()).param("subtype", command.subtype())
                .param("amount", command.value()).param("unit", command.unit())
                .param("sourceType", command.sourceType()).param("producer", command.producer())
                .param("sourceRecord", command.sourceRecordId())
                .param("observed", timestamp(command.observedAt())).param("recorded", timestamp(command.recordedAt()))
                .param("privacy", new SqlArrayValue("text", command.privacyScope().toArray()), Types.ARRAY)
                .param("consent", new SqlArrayValue("text", command.consentScope().toArray()), Types.ARRAY)
                .update();
        if (inserted == 0) {
            throw new PkbSourceRecordConflict("source_conflict");
        }
        jdbc.sql("""
                        INSERT INTO pkb_item_idempotency(producer, user_id, idempotency_key, fingerprint, pkb_item_id)
                        VALUES (:producer, :user, :key, :fingerprint, :id)
                        """)
                .param("producer", command.producer()).param("user", command.userId())
                .param("key", command.idempotencyKey()).param("fingerprint", command.fingerprint())
                .param("id", command.itemId()).update();
        jdbc.sql("""
                        INSERT INTO pkb_provenance(pkb_item_id, source_kind, actor_type, workflow_id, source_reference)
                        VALUES (:id, :kind, 'user', :workflow, :reference)
                        """)
                .param("id", command.itemId()).param("kind", command.sourceType())
                .param("workflow", command.workflowId())
                .param("reference", command.producer() + ":" + command.sourceRecordId()).update();
        // This publisher writes the existing transactional outbox; it does not deliver to Kafka here.
        eventPublisher.publish(PkbDomainEvent.itemCreated(command.userId(), command.itemId(), command.workflowId()));
        return new PkbSourceRecordWriteResult("created",
                find(command.userId(), command.producer(), command.itemId()).orElseThrow());
    }

    public Optional<PkbSourceRecord> find(UUID userId, String producer, UUID itemId) {
        return findRecord(userId, producer, itemId, true);
    }

    public Optional<PkbSourceRecord> findIncludingDeleted(UUID userId, String producer, UUID itemId) {
        return findRecord(userId, producer, itemId, false);
    }

    private Optional<PkbSourceRecord> findRecord(UUID userId, String producer, UUID itemId, boolean activeOnly) {
        return owned(jdbc.sql("SELECT " + FIELDS + JOIN + " WHERE " + OWNED
                        + " AND i.pkb_item_id = :id" + (activeOnly ? ACTIVE : "")), userId, producer)
                .param("id", itemId).query(RECORD_MAPPER).optional();
    }

    public List<PkbSourceRecord> history(UUID userId, String producer, String entityType, String subtype,
                                         Instant from, Instant before, int limit, PkbSourceRecordCursor cursor) {
        // The transport requests one extra row to determine whether another page exists.
        if (limit < 1 || limit > 501 || !from.isBefore(before)) {
            throw new IllegalArgumentException("Invalid history bounds");
        }
        var sql = "SELECT " + FIELDS + JOIN + " WHERE " + OWNED + ACTIVE
                + " AND i.entity_type = :entity AND i.subtype = :subtype"
                + " AND i.observed_at >= :from AND i.observed_at < :before"
                + (cursor == null ? "" : " AND (i.observed_at, i.recorded_at, i.pkb_item_id) > (:cursorObserved, :cursorRecorded, :cursorId)")
                + " ORDER BY i.observed_at, i.recorded_at, i.pkb_item_id LIMIT :limit";
        var query = category(owned(jdbc.sql(sql), userId, producer), entityType, subtype)
                .param("from", timestamp(from)).param("before", timestamp(before)).param("limit", limit);
        if (cursor != null) {
            query.param("cursorObserved", timestamp(cursor.observedAt()))
                    .param("cursorRecorded", timestamp(cursor.recordedAt())).param("cursorId", cursor.itemId());
        }
        return query.query(RECORD_MAPPER).list();
    }

    public List<BigDecimal> totals(UUID userId, String producer, String entityType, String subtype,
                                   String unit, List<PkbQuantityWindow> windows) {
        validateWindows(windows);
        if (windows.isEmpty()) return List.of();
        var values = new StringBuilder();
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) values.append(",");
            values.append("(").append(i).append(", CAST(:from").append(i)
                    .append(" AS timestamptz), CAST(:before").append(i).append(" AS timestamptz))");
        }
        var query = category(owned(jdbc.sql("""
                        SELECT COALESCE(SUM((i.payload ->> 'amount')::numeric), 0) AS total
                        FROM (VALUES %s) AS w(position, starts_at, ends_at)
                        LEFT JOIN pkb_item i ON
                        """.formatted(values) + OWNED + ACTIVE
                        + " AND i.entity_type = :entity AND i.subtype = :subtype AND i.payload ->> 'unit' = :unit"
                        + " AND i.observed_at >= w.starts_at AND i.observed_at < w.ends_at"
                        + " GROUP BY w.position ORDER BY w.position"), userId, producer), entityType, subtype)
                .param("unit", unit);
        for (int i = 0; i < windows.size(); i++) {
            query.param("from" + i, timestamp(windows.get(i).from()))
                    .param("before" + i, timestamp(windows.get(i).before()));
        }
        return query.query((rs, row) -> rs.getBigDecimal("total")).list();
    }

    public List<BigDecimal> recentQuantities(UUID userId, String producer, String entityType,
                                             String subtype, String unit, int limit) {
        if (limit < 1 || limit > 3) throw new IllegalArgumentException("Invalid recent quantity limit");
        return category(owned(jdbc.sql("""
                        SELECT (i.payload ->> 'amount')::numeric AS amount FROM pkb_item i WHERE
                        """ + OWNED + ACTIVE
                        + " AND i.entity_type = :entity AND i.subtype = :subtype AND i.payload ->> 'unit' = :unit"
                        + " GROUP BY (i.payload ->> 'amount')::numeric"
                        + " ORDER BY MAX(i.observed_at) DESC, MAX(i.recorded_at) DESC, amount DESC LIMIT :limit"), userId, producer), entityType, subtype)
                .param("unit", unit).param("limit", limit).query((rs, row) -> rs.getBigDecimal("amount")).list();
    }

    /** Bounded keyset pages of all contributing items; empty windows mean all observation times. */
    public List<PkbSourceRecordAccess> aggregationItems(UUID userId, String producer, String entityType,
            String subtype, String unit, List<PkbQuantityWindow> windows, UUID afterId, int limit) {
        validateWindows(windows);
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("Invalid aggregation page limit");
        var predicates = new StringBuilder();
        for (int index = 0; index < windows.size(); index++) {
            if (index > 0) predicates.append(" OR ");
            predicates.append("(i.observed_at >= :from").append(index)
                    .append(" AND i.observed_at < :before").append(index).append(")");
        }
        var query = category(owned(jdbc.sql("""
                        SELECT i.pkb_item_id, i.source_type, i.privacy_scope, i.consent_scope FROM pkb_item i WHERE
                        """ + OWNED + ACTIVE + " AND i.entity_type = :entity AND i.subtype = :subtype"
                        + " AND i.payload ->> 'unit' = :unit"
                        + (windows.isEmpty() ? "" : " AND (" + predicates + ")")
                        + (afterId == null ? "" : " AND i.pkb_item_id > :after")
                        + " ORDER BY i.pkb_item_id LIMIT :limit"), userId, producer), entityType, subtype)
                .param("unit", unit).param("limit", limit);
        if (afterId != null) query.param("after", afterId);
        for (int index = 0; index < windows.size(); index++) {
            query.param("from" + index, timestamp(windows.get(index).from()))
                    .param("before" + index, timestamp(windows.get(index).before()));
        }
        return query.query((rs, row) -> new PkbSourceRecordAccess(rs.getObject("pkb_item_id", UUID.class),
                rs.getString("source_type"), strings(rs, "privacy_scope"), strings(rs, "consent_scope"))).list();
    }

    private static void validateWindows(List<PkbQuantityWindow> windows) {
        if (windows.size() > 30 || windows.stream().anyMatch(window -> !window.from().isBefore(window.before()))) {
            throw new IllegalArgumentException("Invalid quantity windows");
        }
    }

    @Transactional
    public void delete(UUID userId, String producer, UUID itemId) {
        owned(jdbc.sql("UPDATE pkb_item i SET status = 'deleted', deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE " + OWNED + ACTIVE + " AND i.pkb_item_id = :id"), userId, producer)
                .param("id", itemId).update();
    }

    /** Explicit offline maintenance only. Callers choose a retention cutoff, normally now minus 90 days. */
    @Transactional
    public int purgeDeleted(UUID userId, String producer, Instant deletedBefore, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid purge limit");
        var ids = owned(jdbc.sql("SELECT i.pkb_item_id FROM pkb_item i WHERE " + OWNED
                        + " AND i.deleted_at < :before AND i.status = 'deleted'"
                        + " ORDER BY i.deleted_at, i.pkb_item_id LIMIT :limit FOR UPDATE SKIP LOCKED"), userId, producer)
                .param("before", timestamp(deletedBefore)).param("limit", limit).query(UUID.class).list();
        for (var id : ids) {
            boolean referenced = jdbc.sql("""
                            SELECT EXISTS(SELECT 1 FROM pkb_item WHERE supersedes = :id OR superseded_by = :id)
                                OR EXISTS(SELECT 1 FROM pkb_artifact WHERE pkb_item_id = :id)
                                OR EXISTS(SELECT 1 FROM pkb_relationship WHERE from_item_id = :id OR to_item_id = :id)
                            """)
                    .param("id", id).query(Boolean.class).single();
            if (referenced) throw new PkbSourceRecordConflict("purge_in_use");
            jdbc.sql("""
                            DELETE FROM pkb_processed_event WHERE event_id IN (
                                SELECT event_id FROM pkb_outbox_event WHERE pkb_item_id = :id OR related_pkb_item_id = :id)
                            """).param("id", id).update();
            jdbc.sql("DELETE FROM pkb_outbox_event WHERE pkb_item_id = :id OR related_pkb_item_id = :id")
                    .param("id", id).update();
            owned(jdbc.sql("DELETE FROM pkb_item i WHERE " + OWNED + " AND i.pkb_item_id = :id"), userId, producer)
                    .param("id", id).update();
        }
        return ids.size();
    }

    private static boolean equivalent(PkbSourceRecord record, PkbSourceRecordCreate command) {
        return record.fingerprint().equals(command.fingerprint())
                && record.entityType().equals(command.entityType()) && record.subtype().equals(command.subtype())
                && record.value().compareTo(command.value()) == 0 && record.unit().equals(command.unit())
                && record.sourceType().equals(command.sourceType())
                && new HashSet<>(record.privacyScope()).equals(new HashSet<>(command.privacyScope()))
                && new HashSet<>(record.consentScope()).equals(new HashSet<>(command.consentScope()));
    }

    private static JdbcClient.StatementSpec owned(JdbcClient.StatementSpec query, UUID userId, String producer) {
        return query.param("user", userId).param("producer", producer);
    }

    private static JdbcClient.StatementSpec category(JdbcClient.StatementSpec query, String entityType, String subtype) {
        return query.param("entity", entityType).param("subtype", subtype);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static List<String> strings(ResultSet resultSet, String column) throws SQLException {
        var array = resultSet.getArray(column);
        try {
            return List.copyOf(Arrays.asList((String[]) array.getArray()));
        } finally {
            array.free();
        }
    }
}
