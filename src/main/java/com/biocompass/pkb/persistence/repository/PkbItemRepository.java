package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbItemRepository extends JpaRepository<PkbItemEntity, UUID> {

    Optional<PkbItemEntity> findByPkbItemIdAndUserId(UUID pkbItemId, UUID userId);

    List<PkbItemEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PkbItemEntity> findAllByUserIdAndEntityTypeAndSubtypeOrderByObservedAtDesc(
            UUID userId,
            String entityType,
            String subtype
    );

    List<PkbItemEntity> findAllByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, String status);

    Optional<PkbItemEntity> findByUserIdAndSourceTypeAndSourceId(UUID userId, String sourceType, String sourceId);

    boolean existsByPkbItemIdAndUserId(UUID pkbItemId, UUID userId);
}
