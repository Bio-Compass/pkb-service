package com.biocompass.pkb.persistence;

import com.biocompass.pkb.persistence.dao.PkbArtifactDao;
import com.biocompass.pkb.persistence.dao.PkbConsentBindingDao;
import com.biocompass.pkb.persistence.dao.PkbFactEmbeddingDao;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.dao.PkbRelationshipDao;
import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import com.biocompass.pkb.persistence.entity.PkbConsentBindingEntity;
import com.biocompass.pkb.persistence.entity.PkbFactEmbeddingEntity;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.biocompass.pkb.persistence.entity.PkbProvenanceEntity;
import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class PkbPersistenceDaoIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PkbItemDao itemDao;

    @Autowired
    private PkbRelationshipDao relationshipDao;

    @Autowired
    private PkbArtifactDao artifactDao;

    @Autowired
    private PkbConsentBindingDao consentBindingDao;

    @Autowired
    private PkbFactEmbeddingDao factEmbeddingDao;

    @Test
    void daoLayerPersistsCanonicalPkbRecords() {
        var userId = UUID.randomUUID();
        var observedAt = Instant.parse("2026-06-20T06:15:00Z");

        var waterIntake = waterIntakeItem(userId, observedAt);
        var provenance = healthKitProvenance("healthkit-sync-2026-06-20");

        var savedItem = itemDao.saveWithProvenance(waterIntake, provenance);

        assertThat(savedItem.getPkbItemId()).isNotNull();
        var reloadedItem = itemDao.findByUserAndItemId(userId, savedItem.getPkbItemId()).orElseThrow();
        assertThat(reloadedItem.getPayload())
                .containsEntry("substance", "water")
                .containsEntry("amount", 350)
                .containsEntry("unit", "ml");
        assertThat(reloadedItem.getConsentScope()).containsExactly("nutrition-read");
        assertThat(reloadedItem.getPrivacyScope()).containsExactly("nutrition", "health");
        assertThat(itemDao.findBySource(userId, "healthkit", "healthkit-sample-uuid")).isPresent();
        assertThat(itemDao.findProvenance(savedItem.getPkbItemId()))
                .singleElement()
                .satisfies(savedProvenance -> {
                    assertThat(savedProvenance.getSourceKind()).isEqualTo("healthkit_sync");
                    assertThat(savedProvenance.getSourceReference()).isEqualTo("healthkit-sample-uuid");
                });

        var dailySummary = itemDao.save(dailySummaryItem(userId, observedAt));

        var relationship = PkbRelationshipEntity.builder()
                .userId(userId)
                .fromItemId(savedItem.getPkbItemId())
                .toItemId(dailySummary.getPkbItemId())
                .relationshipType("contributes-to")
                .build();
        relationshipDao.save(relationship);

        assertThat(relationshipDao.findOutgoing(userId, savedItem.getPkbItemId()))
                .singleElement()
                .satisfies(savedRelationship -> {
                    assertThat(savedRelationship.getToItemId()).isEqualTo(dailySummary.getPkbItemId());
                    assertThat(savedRelationship.getRelationshipType()).isEqualTo("contributes-to");
                });

        var artifact = PkbArtifactEntity.builder()
                .userId(userId)
                .pkbItemId(savedItem.getPkbItemId())
                .objectKey("users/%s/documents/water-intake/original.json".formatted(userId))
                .contentType("application/json")
                .sha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .sizeBytes(512L)
                .build();
        var savedArtifact = artifactDao.save(artifact);

        assertThat(artifactDao.findByObjectKey(userId, savedArtifact.getObjectKey()))
                .isPresent()
                .get()
                .extracting(PkbArtifactEntity::getPkbItemId)
                .isEqualTo(savedItem.getPkbItemId());

        var itemConsent = PkbConsentBindingEntity.builder()
                .userId(userId)
                .pkbItemId(savedItem.getPkbItemId())
                .consentReference("consent:nutrition:self")
                .consentScope(new String[]{"nutrition-read"})
                .purposeOfUse("self")
                .build();
        consentBindingDao.save(itemConsent);

        var artifactConsent = PkbConsentBindingEntity.builder()
                .userId(userId)
                .artifactId(savedArtifact.getArtifactId())
                .consentReference("consent:artifact:self")
                .consentScope(new String[]{"artifact-read"})
                .build();
        consentBindingDao.save(artifactConsent);

        assertThat(consentBindingDao.findAllByItem(userId, savedItem.getPkbItemId()))
                .singleElement()
                .extracting(PkbConsentBindingEntity::getConsentReference)
                .isEqualTo("consent:nutrition:self");
        assertThat(consentBindingDao.findAllByArtifact(userId, savedArtifact.getArtifactId()))
                .singleElement()
                .extracting(PkbConsentBindingEntity::getConsentReference)
                .isEqualTo("consent:artifact:self");

        var embedding = PkbFactEmbeddingEntity.builder()
                .userId(userId)
                .pkbItemId(savedItem.getPkbItemId())
                .embedding(testEmbedding())
                .embeddingModel("test-embedding-model")
                .build();
        factEmbeddingDao.save(embedding);

        assertThat(factEmbeddingDao.findByUserAndItemId(userId, savedItem.getPkbItemId()))
                .isPresent()
                .get()
                .satisfies(savedEmbedding -> {
                    assertThat(savedEmbedding.getEmbedding()).hasSize(1536);
                    assertThat(savedEmbedding.getEmbedding()[0]).isEqualTo(0.001f);
                    assertThat(savedEmbedding.getEmbeddingModel()).isEqualTo("test-embedding-model");
                });
    }

    @Test
    void itemCrudCreatesReadsUpdatesAndDeletesItemsAndProvenance() {
        var userId = UUID.randomUUID();
        var observedAt = Instant.parse("2026-06-21T06:30:00Z");
        var item = waterIntakeItem(userId, observedAt, "healthkit-crud-sample-uuid");
        var provenance = healthKitProvenance("healthkit-crud-sync");

        var createdItem = itemDao.saveWithProvenance(item, provenance);
        var itemId = createdItem.getPkbItemId();

        assertThat(itemId).isNotNull();
        assertThat(itemDao.findByUserAndItemId(userId, itemId))
                .isPresent()
                .get()
                .satisfies(savedItem -> {
                    assertThat(savedItem.getStatus()).isEqualTo("active");
                    assertThat(savedItem.getPayload()).containsEntry("amount", 350);
                });
        assertThat(itemDao.findProvenance(itemId))
                .singleElement()
                .satisfies(savedProvenance -> {
                    assertThat(savedProvenance.getProvenanceId()).isNotNull();
                    assertThat(savedProvenance.getWorkflowId()).isEqualTo("healthkit-crud-sync");
                });

        var updatedItem = itemDao.findByUserAndItemId(userId, itemId).orElseThrow();
        updatedItem.setStatus("corrected");
        updatedItem.getPayload().put("amount", 500);
        updatedItem.getPayload().put("correction_reason", "user_edit");
        itemDao.save(updatedItem);

        assertThat(itemDao.findByUserAndItemId(userId, itemId))
                .isPresent()
                .get()
                .satisfies(savedItem -> {
                    assertThat(savedItem.getStatus()).isEqualTo("corrected");
                    assertThat(savedItem.getPayload()).containsEntry("amount", 500);
                    assertThat(savedItem.getPayload()).containsEntry("correction_reason", "user_edit");
                });

        itemDao.deleteProvenance(provenance.getProvenanceId());
        assertThat(itemDao.findProvenance(itemId)).isEmpty();

        itemDao.deleteByUserAndItemId(userId, itemId);
        assertThat(itemDao.findByUserAndItemId(userId, itemId)).isEmpty();
    }

    @Test
    void relationshipCrudCreatesReadsUpdatesAndDeletesRelationships() {
        var userId = UUID.randomUUID();
        var observedAt = Instant.parse("2026-06-22T07:00:00Z");
        var sourceItem = itemDao.save(waterIntakeItem(userId, observedAt, "relationship-source"));
        var targetItem = itemDao.save(dailySummaryItem(userId, observedAt));

        var relationship = PkbRelationshipEntity.builder()
                .userId(userId)
                .fromItemId(sourceItem.getPkbItemId())
                .toItemId(targetItem.getPkbItemId())
                .relationshipType("contributes-to")
                .build();

        var createdRelationship = relationshipDao.save(relationship);
        var relationshipId = createdRelationship.getRelationshipId();

        assertThat(relationshipDao.findByUserAndRelationshipId(userId, relationshipId))
                .isPresent()
                .get()
                .satisfies(savedRelationship -> {
                    assertThat(savedRelationship.getFromItemId()).isEqualTo(sourceItem.getPkbItemId());
                    assertThat(savedRelationship.getToItemId()).isEqualTo(targetItem.getPkbItemId());
                    assertThat(savedRelationship.getRelationshipType()).isEqualTo("contributes-to");
                });

        createdRelationship.setRelationshipType("derived-from");
        relationshipDao.save(createdRelationship);

        assertThat(relationshipDao.findByUserAndRelationshipId(userId, relationshipId))
                .isPresent()
                .get()
                .extracting(PkbRelationshipEntity::getRelationshipType)
                .isEqualTo("derived-from");
        assertThat(relationshipDao.findIncoming(userId, targetItem.getPkbItemId()))
                .singleElement()
                .extracting(PkbRelationshipEntity::getRelationshipId)
                .isEqualTo(relationshipId);

        relationshipDao.deleteByUserAndRelationshipId(userId, relationshipId);
        assertThat(relationshipDao.findByUserAndRelationshipId(userId, relationshipId)).isEmpty();
        assertThat(relationshipDao.findOutgoing(userId, sourceItem.getPkbItemId())).isEmpty();
    }

    @Test
    void artifactConsentAndEmbeddingCrudCreatesReadsUpdatesAndDeletesRecords() {
        var userId = UUID.randomUUID();
        var observedAt = Instant.parse("2026-06-23T08:00:00Z");
        var item = itemDao.save(waterIntakeItem(userId, observedAt, "artifact-consent-embedding-source"));

        var artifact = PkbArtifactEntity.builder()
                .userId(userId)
                .pkbItemId(item.getPkbItemId())
                .objectKey("users/%s/documents/crud/original.json".formatted(userId))
                .contentType("application/json")
                .sha256("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
                .sizeBytes(256L)
                .build();

        var createdArtifact = artifactDao.save(artifact);
        var artifactId = createdArtifact.getArtifactId();

        assertThat(artifactDao.findByUserAndArtifactId(userId, artifactId))
                .isPresent()
                .get()
                .satisfies(savedArtifact -> {
                    assertThat(savedArtifact.getPkbItemId()).isEqualTo(item.getPkbItemId());
                    assertThat(savedArtifact.getSizeBytes()).isEqualTo(256L);
                });

        createdArtifact.setSizeBytes(768L);
        createdArtifact.setContentType("application/vnd.biocompass.water+json");
        artifactDao.save(createdArtifact);

        assertThat(artifactDao.findByUserAndArtifactId(userId, artifactId))
                .isPresent()
                .get()
                .satisfies(savedArtifact -> {
                    assertThat(savedArtifact.getSizeBytes()).isEqualTo(768L);
                    assertThat(savedArtifact.getContentType()).isEqualTo("application/vnd.biocompass.water+json");
                });

        var consentBinding = PkbConsentBindingEntity.builder()
                .userId(userId)
                .artifactId(artifactId)
                .consentReference("consent:crud:artifact")
                .consentScope(new String[]{"artifact-read"})
                .purposeOfUse("self")
                .build();

        var createdConsentBinding = consentBindingDao.save(consentBinding);
        var consentBindingId = createdConsentBinding.getConsentBindingId();

        assertThat(consentBindingDao.findByUserAndConsentBindingId(userId, consentBindingId))
                .isPresent()
                .get()
                .satisfies(savedConsent -> {
                    assertThat(savedConsent.getArtifactId()).isEqualTo(artifactId);
                    assertThat(savedConsent.getConsentScope()).containsExactly("artifact-read");
                });

        createdConsentBinding.setPurposeOfUse("export");
        createdConsentBinding.setConsentScope(new String[]{"artifact-read", "artifact-export"});
        consentBindingDao.save(createdConsentBinding);

        assertThat(consentBindingDao.findByUserAndConsentBindingId(userId, consentBindingId))
                .isPresent()
                .get()
                .satisfies(savedConsent -> {
                    assertThat(savedConsent.getPurposeOfUse()).isEqualTo("export");
                    assertThat(savedConsent.getConsentScope()).containsExactly("artifact-read", "artifact-export");
                });

        var embedding = PkbFactEmbeddingEntity.builder()
                .userId(userId)
                .pkbItemId(item.getPkbItemId())
                .embedding(testEmbedding())
                .embeddingModel("crud-embedding-v1")
                .build();

        var createdEmbedding = factEmbeddingDao.save(embedding);

        assertThat(factEmbeddingDao.findByUserAndItemId(userId, item.getPkbItemId()))
                .isPresent()
                .get()
                .satisfies(savedEmbedding -> {
                    assertThat(savedEmbedding.getEmbedding()).hasSize(1536);
                    assertThat(savedEmbedding.getEmbedding()[1]).isEqualTo(0.002f);
                    assertThat(savedEmbedding.getEmbeddingModel()).isEqualTo("crud-embedding-v1");
                });

        createdEmbedding.setEmbeddingModel("crud-embedding-v2");
        var updatedEmbedding = testEmbedding();
        updatedEmbedding[1] = 0.123f;
        createdEmbedding.setEmbedding(updatedEmbedding);
        factEmbeddingDao.save(createdEmbedding);

        assertThat(factEmbeddingDao.findByUserAndItemId(userId, item.getPkbItemId()))
                .isPresent()
                .get()
                .satisfies(savedEmbedding -> {
                    assertThat(savedEmbedding.getEmbedding()[1]).isEqualTo(0.123f);
                    assertThat(savedEmbedding.getEmbeddingModel()).isEqualTo("crud-embedding-v2");
                });

        factEmbeddingDao.deleteByUserAndItemId(userId, item.getPkbItemId());
        assertThat(factEmbeddingDao.findByUserAndItemId(userId, item.getPkbItemId())).isEmpty();

        consentBindingDao.deleteByUserAndConsentBindingId(userId, consentBindingId);
        assertThat(consentBindingDao.findByUserAndConsentBindingId(userId, consentBindingId)).isEmpty();

        artifactDao.deleteByUserAndArtifactId(userId, artifactId);
        assertThat(artifactDao.findByUserAndArtifactId(userId, artifactId)).isEmpty();
    }

    private static PkbItemEntity waterIntakeItem(UUID userId, Instant observedAt) {
        return waterIntakeItem(userId, observedAt, "healthkit-sample-uuid");
    }

    private static PkbItemEntity waterIntakeItem(UUID userId, Instant observedAt, String sourceId) {
        return PkbItemEntity.builder()
                .userId(userId)
                .entityType("nutrition_intake")
                .subtype("water")
                .status("active")
                .payload(new LinkedHashMap<>(Map.of(
                        "substance", "water",
                        "amount", 350,
                        "unit", "ml",
                        "start_time", "2026-06-20T08:15:00+02:00",
                        "end_time", "2026-06-20T08:15:00+02:00",
                        "source_app", "iphone",
                        "source_platform", "apple_healthkit"
                )))
                .sourceType("healthkit")
                .sourceId(sourceId)
                .observedAt(observedAt)
                .validFrom(observedAt)
                .validUntil(observedAt)
                .consentScope(new String[]{"nutrition-read"})
                .privacyScope(new String[]{"nutrition", "health"})
                .verificationStatus("user_reported")
                .build();
    }

    private static PkbItemEntity dailySummaryItem(UUID userId, Instant observedAt) {
        return PkbItemEntity.builder()
                .userId(userId)
                .entityType("nutrition_summary")
                .subtype("daily_water_total")
                .status("active")
                .payload(new LinkedHashMap<>(Map.of(
                        "amount", 350,
                        "unit", "ml",
                        "date", "2026-06-20"
                )))
                .sourceType("derived")
                .sourceId("daily-water-total-2026-06-20")
                .observedAt(observedAt)
                .validFrom(Instant.parse("2026-06-20T00:00:00Z"))
                .validUntil(Instant.parse("2026-06-20T23:59:59Z"))
                .privacyScope(new String[]{"nutrition", "health"})
                .verificationStatus("derived")
                .build();
    }

    private static PkbProvenanceEntity healthKitProvenance(String workflowId) {
        return PkbProvenanceEntity.builder()
                .sourceKind("healthkit_sync")
                .actorType("system")
                .workflowId(workflowId)
                .sourceReference("healthkit-sample-uuid")
                .extractionMethod("healthkit-water-intake-import-v1")
                .build();
    }

    private static float[] testEmbedding() {
        var embedding = new float[1536];
        embedding[0] = 0.001f;
        embedding[1] = 0.002f;
        embedding[2] = 0.003f;
        return embedding;
    }
}
