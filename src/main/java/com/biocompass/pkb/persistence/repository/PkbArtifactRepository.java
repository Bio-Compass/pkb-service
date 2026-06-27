package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbArtifactRepository extends JpaRepository<PkbArtifactEntity, UUID> {

    Optional<PkbArtifactEntity> findByArtifactIdAndUserId(UUID artifactId, UUID userId);

    Optional<PkbArtifactEntity> findByUserIdAndObjectKey(UUID userId, String objectKey);

    List<PkbArtifactEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PkbArtifactEntity> findAllByUserIdAndPkbItemIdOrderByCreatedAtDesc(UUID userId, UUID pkbItemId);
}
