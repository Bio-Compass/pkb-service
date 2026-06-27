package com.biocompass.pkb.persistence.dao;

import com.biocompass.pkb.persistence.entity.PkbRelationshipEntity;
import com.biocompass.pkb.persistence.repository.PkbRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbRelationshipDao {

    private final PkbRelationshipRepository relationshipRepository;

    @Transactional
    public PkbRelationshipEntity save(PkbRelationshipEntity relationship) {
        return relationshipRepository.save(relationship);
    }

    @Transactional(readOnly = true)
    public Optional<PkbRelationshipEntity> findByUserAndRelationshipId(UUID userId, UUID relationshipId) {
        return relationshipRepository.findByRelationshipIdAndUserId(relationshipId, userId);
    }

    @Transactional(readOnly = true)
    public List<PkbRelationshipEntity> findOutgoing(UUID userId, UUID fromItemId) {
        return relationshipRepository.findAllByUserIdAndFromItemId(userId, fromItemId);
    }

    @Transactional(readOnly = true)
    public List<PkbRelationshipEntity> findIncoming(UUID userId, UUID toItemId) {
        return relationshipRepository.findAllByUserIdAndToItemId(userId, toItemId);
    }

    @Transactional(readOnly = true)
    public List<PkbRelationshipEntity> findByType(UUID userId, String relationshipType) {
        return relationshipRepository.findAllByUserIdAndRelationshipType(userId, relationshipType);
    }

    @Transactional
    public void deleteByUserAndRelationshipId(UUID userId, UUID relationshipId) {
        relationshipRepository.findByRelationshipIdAndUserId(relationshipId, userId)
                .ifPresent(relationshipRepository::delete);
    }
}
