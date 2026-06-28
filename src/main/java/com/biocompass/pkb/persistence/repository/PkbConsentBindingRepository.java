package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbConsentBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbConsentBindingRepository extends JpaRepository<PkbConsentBindingEntity, UUID> {

    Optional<PkbConsentBindingEntity> findByConsentBindingIdAndUserId(UUID consentBindingId, UUID userId);

    List<PkbConsentBindingEntity> findAllByUserIdAndPkbItemIdOrderByCreatedAtDesc(UUID userId, UUID pkbItemId);

    List<PkbConsentBindingEntity> findAllByUserIdAndArtifactIdOrderByCreatedAtDesc(UUID userId, UUID artifactId);
}
