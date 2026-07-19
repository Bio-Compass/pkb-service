package com.biocompass.pkb.persistence.dao;

import com.biocompass.pkb.persistence.entity.PkbArtifactProvenanceEntity;
import com.biocompass.pkb.persistence.repository.PkbArtifactProvenanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbArtifactProvenanceDao {

    private final PkbArtifactProvenanceRepository artifactProvenanceRepository;

    @Transactional
    public PkbArtifactProvenanceEntity save(PkbArtifactProvenanceEntity artifactProvenance) {
        return artifactProvenanceRepository.save(artifactProvenance);
    }

    @Transactional(readOnly = true)
    public Optional<PkbArtifactProvenanceEntity> findByUserAndArtifactProvenanceId(
            UUID userId,
            UUID artifactProvenanceId
    ) {
        return artifactProvenanceRepository.findByArtifactProvenanceIdAndUserId(artifactProvenanceId, userId);
    }

    @Transactional(readOnly = true)
    public List<PkbArtifactProvenanceEntity> findAllByArtifact(UUID userId, UUID artifactId) {
        return artifactProvenanceRepository.findAllByUserIdAndArtifactIdOrderByCreatedAtDesc(userId, artifactId);
    }
}
