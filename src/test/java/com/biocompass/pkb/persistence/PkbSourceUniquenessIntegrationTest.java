package com.biocompass.pkb.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PkbSourceUniquenessIntegrationTest extends AbstractPostgresIntegrationTest {

    @BeforeAll
    static void migrateSchema() {
        migrateSchemaWithFlyway();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsDuplicateSourceWithinAndAcrossUsers(boolean differentUser) throws Exception {
        var userId = UUID.randomUUID();
        var sourceId = UUID.randomUUID().toString();
        var originalId = UUID.randomUUID();
        insertItem(originalId, userId, sourceId);

        assertThatThrownBy(() -> insertItem(UUID.randomUUID(), differentUser ? UUID.randomUUID() : userId, sourceId))
                .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23505"))
                .hasMessageContaining("uq_pkb_item_source");
        assertThat(queryStrings("SELECT pkb_item_id::text FROM pkb_item WHERE source_type = 'manual' AND source_id = ?", sourceId))
                .containsExactly(originalId.toString());
    }

    @Test
    void allowsDifferentSourceTypesCaseSensitiveIdentifiersAndMultipleNulls() throws Exception {
        var userId = UUID.randomUUID();
        var sourceId = UUID.randomUUID().toString();
        insertItem(UUID.randomUUID(), userId, sourceId + "A");
        insertItem(UUID.randomUUID(), userId, sourceId + "a");
        insertItem(UUID.randomUUID(), userId, sourceId);
        execute("""
                INSERT INTO pkb_item (user_id, entity_type, subtype, status, payload, source_type, source_id)
                VALUES (?, 'observation', 'note', 'active', '{}'::jsonb, 'device', ?)
                """, userId, sourceId);
        insertItem(UUID.randomUUID(), userId, null);
        insertItem(UUID.randomUUID(), userId, null);

        assertThat(queryStrings("SELECT pkb_item_id::text FROM pkb_item WHERE user_id = ?", userId)).hasSize(6);
    }

    @Test
    void supersededItemsKeepTheirSourceIdentifierReserved() throws Exception {
        var userId = UUID.randomUUID();
        var originalId = UUID.randomUUID();
        var replacementId = UUID.randomUUID();
        var sourceId = UUID.randomUUID().toString();
        insertItem(originalId, userId, sourceId);
        insertItem(replacementId, userId, UUID.randomUUID().toString());
        execute("UPDATE pkb_item SET status = 'superseded', superseded_by = ? WHERE pkb_item_id = ?", replacementId, originalId);
        execute("UPDATE pkb_item SET supersedes = ? WHERE pkb_item_id = ?", originalId, replacementId);

        assertThatThrownBy(() -> insertItem(UUID.randomUUID(), userId, sourceId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uq_pkb_item_source");
    }

    @Test
    void freshSchemaHasPartialUniqueIndexInsteadOfRedundantIndex() throws Exception {
        assertThat(queryStrings("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'uq_pkb_item_source'"))
                .singleElement().asString()
                .contains("CREATE UNIQUE INDEX", "(source_type, source_id)", "WHERE (source_id IS NOT NULL)");
        assertThat(queryStrings("SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_pkb_item_source'"))
                .isEmpty();
    }

    @Test
    void upgradesEmptyDatabaseWithoutChangingEarlierMigrations() throws Exception {
        var databaseName = "pkb_source_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE " + databaseName);
        var url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
        try {
            Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                    .target("5").load().migrate();
            Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword()).load().migrate();

            try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT count(*) FROM pg_indexes
                         WHERE schemaname = 'public' AND indexname = 'uq_pkb_item_source'
                         """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        } finally {
            execute("DROP DATABASE " + databaseName);
        }
    }

    @Test
    void concurrentDuplicateWaitsForWinnerAndThenFails() throws Exception {
        var sourceId = UUID.randomUUID().toString();
        var winnerId = UUID.randomUUID();
        var loserPid = new AtomicInteger();
        // Close the winning connection before the executor so failed assertions also release the lock.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var winner = connection()) {
            winner.setAutoCommit(false);
            insert(winner, winnerId, sourceId);
            var loser = executor.submit(() -> {
                try (var connection = connection()) {
                    connection.setAutoCommit(false);
                    try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                        result.next();
                        loserPid.set(result.getInt(1));
                    }
                    try {
                        insert(connection, UUID.randomUUID(), sourceId);
                        connection.commit();
                        return "committed";
                    } catch (SQLException error) {
                        connection.rollback();
                        return error.getSQLState();
                    }
                }
            });

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(queryStrings("""
                            SELECT wait_event_type FROM pg_stat_activity
                            WHERE pid = ? AND cardinality(pg_blocking_pids(pid)) > 0
                            """, loserPid.get())).containsExactly("Lock"));
            winner.commit();

            assertThat(loser.get(10, TimeUnit.SECONDS)).isEqualTo("23505");
            assertThat(queryStrings("SELECT pkb_item_id::text FROM pkb_item WHERE source_type = 'manual' AND source_id = ?", sourceId))
                    .containsExactly(winnerId.toString());
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void insert(Connection connection, UUID itemId, String sourceId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO pkb_item (pkb_item_id, user_id, entity_type, subtype, status, payload, source_type, source_id)
                VALUES (?, ?, 'observation', 'note', 'active', '{}'::jsonb, 'manual', ?)
                """)) {
            statement.setQueryTimeout(15);
            statement.setObject(1, itemId);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, sourceId);
            statement.executeUpdate();
        }
    }
}
