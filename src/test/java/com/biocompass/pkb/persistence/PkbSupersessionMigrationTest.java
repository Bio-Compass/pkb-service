package com.biocompass.pkb.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PkbSupersessionMigrationTest extends AbstractPostgresIntegrationTest {
    @Test
    void freshDatabaseEnforcesOneReplacementAndAllowsNullsAndSeparateChains() throws Exception {
        var url = newDatabase();
        flyway(url, "latest").migrate();
        var user = UUID.randomUUID();
        var original = UUID.randomUUID();
        var otherOriginal = UUID.randomUUID();
        insert(url, user, original, null);
        insert(url, user, otherOriginal, null);
        insert(url, user, UUID.randomUUID(), original);
        insert(url, user, UUID.randomUUID(), otherOriginal);
        assertThatThrownBy(() -> insert(url, user, UUID.randomUUID(), original))
                .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("uq_pkb_item_supersedes");
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            var result = statement.executeQuery("select indexname from pg_indexes where tablename='pkb_item'");
            var indexes = new java.util.ArrayList<String>();
            while (result.next()) indexes.add(result.getString(1));
            assertThat(indexes).contains("uq_pkb_item_supersedes").doesNotContain("idx_pkb_item_supersedes");
        }
    }

    @Test
    void duplicateHistoryStopsMigrationWithoutChangingRecordsAndCanBeManuallyResolved() throws Exception {
        var url = newDatabase();
        flyway(url, "5").migrate();
        var user = UUID.randomUUID();
        var original = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        insert(url, user, original, null);
        insert(url, user, first, original);
        insert(url, user, second, original);
        assertThatThrownBy(() -> flyway(url, "latest").migrate())
                .hasStackTraceContaining("duplicate supersedes values exist")
                .hasStackTraceContaining("resolve them manually");
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            var result = statement.executeQuery("select count(*) from pkb_item where supersedes is not null");
            result.next();
            assertThat(result.getInt(1)).isEqualTo(2);
            result = statement.executeQuery("select count(*) from flyway_schema_history where version='6'");
            result.next();
            assertThat(result.getInt(1)).isZero();
            // Simulate an explicit operator decision; the migration itself never chooses a winner.
            try (var repair = connection.prepareStatement("update pkb_item set supersedes=null where pkb_item_id=?")) {
                repair.setObject(1, second);
                assertThat(repair.executeUpdate()).isEqualTo(1);
            }
        }
        flyway(url, "latest").migrate();
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("select count(*) from pkb_item")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }

    private static Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .target(target).load();
    }
    private static String newDatabase() throws Exception {
        var name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE " + name);
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
    }
    private static void insert(String url, UUID user, UUID item, UUID supersedes) throws Exception {
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("""
                     insert into pkb_item(pkb_item_id,user_id,entity_type,subtype,status,payload,source_type,supersedes)
                     values(?,?,'observation','note','active','{}'::jsonb,'test',?)
                     """)) {
            statement.setObject(1, item);
            statement.setObject(2, user);
            statement.setObject(3, supersedes);
            statement.executeUpdate();
        }
    }
}
