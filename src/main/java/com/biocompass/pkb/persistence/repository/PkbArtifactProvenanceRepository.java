package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbArtifactProvenanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbArtifactProvenanceRepository extends JpaRepository<PkbArtifactProvenanceEntity, UUID> {

    Optional<PkbArtifactProvenanceEntity> findByArtifactProvenanceIdAndUserId(
            UUID artifactProvenanceId,
            UUID userId
    );

    List<PkbArtifactProvenanceEntity> findAllByUserIdAndArtifactIdOrderByCreatedAtDesc(
            UUID userId,
            UUID artifactId
    );
}
