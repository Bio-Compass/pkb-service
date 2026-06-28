package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbFactEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PkbFactEmbeddingRepository extends JpaRepository<PkbFactEmbeddingEntity, UUID> {

    Optional<PkbFactEmbeddingEntity> findByPkbItemIdAndUserId(UUID pkbItemId, UUID userId);

    boolean existsByPkbItemIdAndUserId(UUID pkbItemId, UUID userId);
}
