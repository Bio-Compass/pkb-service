package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbItemRepository extends JpaRepository<PkbItemEntity, UUID>, JpaSpecificationExecutor<PkbItemEntity> {

    Optional<PkbItemEntity> findByPkbItemIdAndUserId(UUID pkbItemId, UUID userId);

    List<PkbItemEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PkbItemEntity> findAllByUserIdAndEntityTypeAndSubtypeOrderByObservedAtDesc(
            UUID userId,
            String entityType,
            String subtype
    );

    Optional<PkbItemEntity> findByUserIdAndSourceTypeAndSourceId(UUID userId, String sourceType, String sourceId);

}
