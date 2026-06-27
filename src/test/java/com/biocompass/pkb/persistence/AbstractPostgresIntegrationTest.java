package com.biocompass.pkb.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Testcontainers
abstract class AbstractPostgresIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg17-trixie")
            .asCompatibleSubstituteFor("postgres");

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("pkb")
            .withUsername("pkb")
            .withPassword("pkb-local-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    protected static void migrateSchemaWithFlyway() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    protected static void insertItem(UUID itemId, UUID userId, String sourceId) throws SQLException {
        execute("""
                INSERT INTO pkb_item (
                    pkb_item_id,
                    user_id,
                    entity_type,
                    subtype,
                    status,
                    payload,
                    source_type,
                    source_id
                )
                VALUES (?, ?, 'observation', 'note', 'active', '{"text":"headache"}'::jsonb, 'manual', ?)
                """, itemId, userId, sourceId);
    }

    protected static Set<String> queryStrings(String sql, Object... parameters) throws SQLException {
        try (var connection = connection();
             var statement = prepare(connection, sql, parameters);
             var resultSet = statement.executeQuery()) {
            var values = new LinkedHashSet<String>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        }
    }

    protected static Map<String, String> columnTypes(String tableName) throws SQLException {
        try (var connection = connection();
             var statement = prepare(connection, """
                     SELECT column_name, udt_name
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = ?
                     """, tableName);
             var resultSet = statement.executeQuery()) {
            var columnTypes = new LinkedHashMap<String, String>();
            while (resultSet.next()) {
                columnTypes.put(resultSet.getString("column_name"), resultSet.getString("udt_name"));
            }
            return columnTypes;
        }
    }

    protected static int execute(String sql, Object... parameters) throws SQLException {
        try (var connection = connection();
             var statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException {
        var statement = connection.prepareStatement(sql);
        for (var index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
        return statement;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
