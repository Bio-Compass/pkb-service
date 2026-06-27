package com.biocompass.pkb.persistence.dao;

import com.biocompass.pkb.persistence.entity.PkbArtifactEntity;
import com.biocompass.pkb.persistence.repository.PkbArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbArtifactDao {

    private final PkbArtifactRepository artifactRepository;

    @Transactional
    public PkbArtifactEntity save(PkbArtifactEntity artifact) {
        return artifactRepository.save(artifact);
    }

    @Transactional(readOnly = true)
    public Optional<PkbArtifactEntity> findByUserAndArtifactId(UUID userId, UUID artifactId) {
        return artifactRepository.findByArtifactIdAndUserId(artifactId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<PkbArtifactEntity> findByObjectKey(UUID userId, String objectKey) {
        return artifactRepository.findByUserIdAndObjectKey(userId, objectKey);
    }

    @Transactional(readOnly = true)
    public List<PkbArtifactEntity> findAllByUser(UUID userId) {
        return artifactRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<PkbArtifactEntity> findAllByItem(UUID userId, UUID pkbItemId) {
        return artifactRepository.findAllByUserIdAndPkbItemIdOrderByCreatedAtDesc(userId, pkbItemId);
    }

    @Transactional
    public void deleteByUserAndArtifactId(UUID userId, UUID artifactId) {
        artifactRepository.findByArtifactIdAndUserId(artifactId, userId)
                .ifPresent(artifactRepository::delete);
    }
}
