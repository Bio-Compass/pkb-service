package com.biocompass.pkb.artifact;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkbArtifactObjectKeyGeneratorTest {

    private final PkbArtifactObjectKeyGenerator generator = new PkbArtifactObjectKeyGenerator();

    @Test
    void generatesDocumentObjectKeysUsingArchitectureConvention() {
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var documentId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(generator.documentObjectKey(userId, documentId, "original"))
                .isEqualTo("users/11111111-1111-1111-1111-111111111111/documents/22222222-2222-2222-2222-222222222222/original");
        assertThat(generator.documentObjectKey(userId, documentId, "ocr.json"))
                .isEqualTo("users/11111111-1111-1111-1111-111111111111/documents/22222222-2222-2222-2222-222222222222/ocr.json");
        assertThat(generator.documentObjectKey(userId, documentId, "thumbnail.webp"))
                .isEqualTo("users/11111111-1111-1111-1111-111111111111/documents/22222222-2222-2222-2222-222222222222/thumbnail.webp");
    }

    @Test
    void rejectsObjectNamesThatCanEscapeTheDocumentPrefix() {
        var userId = UUID.randomUUID();
        var documentId = UUID.randomUUID();

        assertThatThrownBy(() -> generator.documentObjectKey(userId, documentId, "../original.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectName");
        assertThatThrownBy(() -> generator.documentObjectKey(userId, documentId, "reports/original.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectName");
    }
}
