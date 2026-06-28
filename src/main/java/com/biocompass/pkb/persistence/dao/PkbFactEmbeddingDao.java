package com.biocompass.pkb.persistence.dao;

import com.biocompass.pkb.persistence.entity.PkbFactEmbeddingEntity;
import com.biocompass.pkb.persistence.repository.PkbFactEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbFactEmbeddingDao {

    private final PkbFactEmbeddingRepository factEmbeddingRepository;

    @Transactional
    public PkbFactEmbeddingEntity save(PkbFactEmbeddingEntity factEmbedding) {
        return factEmbeddingRepository.save(factEmbedding);
    }

    @Transactional(readOnly = true)
    public Optional<PkbFactEmbeddingEntity> findByUserAndItemId(UUID userId, UUID pkbItemId) {
        return factEmbeddingRepository.findByPkbItemIdAndUserId(pkbItemId, userId);
    }

    @Transactional
    public void deleteByUserAndItemId(UUID userId, UUID pkbItemId) {
        factEmbeddingRepository.findByPkbItemIdAndUserId(pkbItemId, userId)
                .ifPresent(factEmbeddingRepository::delete);
    }
}
