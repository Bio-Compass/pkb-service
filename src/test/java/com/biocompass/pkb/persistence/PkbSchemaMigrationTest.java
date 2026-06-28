package com.biocompass.pkb.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbSchemaMigrationTest extends AbstractPostgresIntegrationTest {

    @BeforeAll
    static void migrateSchema() {
        migrateSchemaWithFlyway();
    }

    @Test
    void migrationCreatesCanonicalTablesAndExtensions() throws Exception {
        assertThat(queryStrings("SELECT extname FROM pg_extension WHERE extname IN ('pgcrypto', 'vector')"))
                .containsExactlyInAnyOrder("pgcrypto", "vector");

        assertThat(queryStrings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """))
                .contains(
                        "pkb_item",
                        "pkb_provenance",
                        "pkb_relationship",
                        "pkb_consent_binding",
                        "pkb_artifact",
                        "pkb_fact_embedding");

        assertThat(columnTypes("pkb_item"))
                .containsEntry("payload", "jsonb")
                .containsEntry("consent_scope", "_text")
                .containsEntry("privacy_scope", "_text")
                .containsEntry("search_document", "tsvector");
        assertThat(columnTypes("pkb_fact_embedding"))
                .containsEntry("embedding", "vector");
    }

    @Test
    void migrationCreatesQueryCriticalIndexes() throws Exception {
        assertThat(queryStrings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """))
                .contains(
                        "idx_pkb_item_user_status",
                        "idx_pkb_item_user_entity_type_subtype",
                        "idx_pkb_item_user_validity",
                        "idx_pkb_item_payload_gin",
                        "idx_pkb_item_privacy_scope_gin",
                        "idx_pkb_item_search_document_gin",
                        "idx_pkb_relationship_user_type",
                        "idx_pkb_artifact_user_item",
                        "idx_pkb_consent_binding_scope_gin",
                        "idx_pkb_fact_embedding_vector_hnsw");
    }

    @Test
    void schemaEnforcesRelationshipAndArtifactOwnership() throws Exception {
        var firstUserId = UUID.randomUUID();
        var secondUserId = UUID.randomUUID();
        var firstItemId = UUID.randomUUID();
        var secondItemId = UUID.randomUUID();
        var thirdItemId = UUID.randomUUID();

        insertItem(firstItemId, firstUserId, "manual-a");
        insertItem(secondItemId, secondUserId, "manual-b");
        insertItem(thirdItemId, firstUserId, "manual-c");

        execute("""
                INSERT INTO pkb_relationship (user_id, from_item_id, to_item_id, relationship_type)
                VALUES (?, ?, ?, 'supports')
                """, firstUserId, firstItemId, thirdItemId);

        assertThatThrownBy(() -> execute("""
                INSERT INTO pkb_relationship (user_id, from_item_id, to_item_id, relationship_type)
                VALUES (?, ?, ?, 'supports')
                """, firstUserId, firstItemId, secondItemId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_pkb_relationship_to_item_owner");

        assertThatThrownBy(() -> execute("""
                INSERT INTO pkb_relationship (user_id, from_item_id, to_item_id, relationship_type)
                VALUES (?, ?, ?, 'same-as')
                """, firstUserId, firstItemId, firstItemId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_pkb_relationship_not_self");

        assertThatThrownBy(() -> execute("""
                INSERT INTO pkb_artifact (user_id, pkb_item_id, object_key)
                VALUES (?, ?, 'users/test/documents/mismatched/original.pdf')
                """, firstUserId, secondItemId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_pkb_artifact_item_owner");
    }

    @Test
    void schemaEnforcesConsentBindingOwnershipAndSubject() throws Exception {
        var userId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var artifactId = UUID.randomUUID();

        insertItem(itemId, userId, "manual-consent");
        execute("""
                INSERT INTO pkb_artifact (artifact_id, user_id, pkb_item_id, object_key)
                VALUES (?, ?, ?, 'users/test/documents/consent/original.pdf')
                """, artifactId, userId, itemId);

        execute("""
                INSERT INTO pkb_consent_binding (user_id, pkb_item_id, consent_reference)
                VALUES (?, ?, 'consent:item')
                """, userId, itemId);

        execute("""
                INSERT INTO pkb_consent_binding (user_id, artifact_id, consent_reference)
                VALUES (?, ?, 'consent:artifact')
                """, userId, artifactId);

        assertThatThrownBy(() -> execute("""
                INSERT INTO pkb_consent_binding (user_id, pkb_item_id, artifact_id, consent_reference)
                VALUES (?, ?, ?, 'consent:ambiguous')
                """, userId, itemId, artifactId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_pkb_consent_binding_subject");

        assertThatThrownBy(() -> execute("""
                INSERT INTO pkb_consent_binding (user_id, artifact_id, consent_reference)
                VALUES (?, ?, 'consent:mismatched')
                """, otherUserId, artifactId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_pkb_consent_binding_artifact_owner");
    }
}
