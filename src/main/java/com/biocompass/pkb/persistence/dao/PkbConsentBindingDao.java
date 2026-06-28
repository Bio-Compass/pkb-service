package com.biocompass.pkb.persistence.dao;

import com.biocompass.pkb.persistence.entity.PkbConsentBindingEntity;
import com.biocompass.pkb.persistence.repository.PkbConsentBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbConsentBindingDao {

    private final PkbConsentBindingRepository consentBindingRepository;

    @Transactional
    public PkbConsentBindingEntity save(PkbConsentBindingEntity consentBinding) {
        return consentBindingRepository.save(consentBinding);
    }

    @Transactional(readOnly = true)
    public Optional<PkbConsentBindingEntity> findByUserAndConsentBindingId(UUID userId, UUID consentBindingId) {
        return consentBindingRepository.findByConsentBindingIdAndUserId(consentBindingId, userId);
    }

    @Transactional(readOnly = true)
    public List<PkbConsentBindingEntity> findAllByItem(UUID userId, UUID pkbItemId) {
        return consentBindingRepository.findAllByUserIdAndPkbItemIdOrderByCreatedAtDesc(userId, pkbItemId);
    }

    @Transactional(readOnly = true)
    public List<PkbConsentBindingEntity> findAllByArtifact(UUID userId, UUID artifactId) {
        return consentBindingRepository.findAllByUserIdAndArtifactIdOrderByCreatedAtDesc(userId, artifactId);
    }

    @Transactional
    public void deleteByUserAndConsentBindingId(UUID userId, UUID consentBindingId) {
        consentBindingRepository.findByConsentBindingIdAndUserId(consentBindingId, userId)
                .ifPresent(consentBindingRepository::delete);
    }
}
