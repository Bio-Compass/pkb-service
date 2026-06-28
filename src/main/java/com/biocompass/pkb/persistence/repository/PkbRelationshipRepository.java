package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbRelationshipRepository extends JpaRepository<PkbRelationshipEntity, UUID> {

    Optional<PkbRelationshipEntity> findByRelationshipIdAndUserId(UUID relationshipId, UUID userId);

    List<PkbRelationshipEntity> findAllByUserIdAndFromItemId(UUID userId, UUID fromItemId);

    List<PkbRelationshipEntity> findAllByUserIdAndToItemId(UUID userId, UUID toItemId);

    List<PkbRelationshipEntity> findAllByUserIdAndRelationshipType(UUID userId, String relationshipType);
}
