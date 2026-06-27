package com.biocompass.pkb.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Entity
@Table(name = "pkb_fact_embedding")
public class PkbFactEmbeddingEntity {

    @Id
    @Column(name = "pkb_item_id", nullable = false)
    private UUID pkbItemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = PkbVectorConverter.class)
    @ColumnTransformer(write = "?::vector")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
