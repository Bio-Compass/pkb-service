package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbProvenanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PkbProvenanceRepository extends JpaRepository<PkbProvenanceEntity, UUID> {

    List<PkbProvenanceEntity> findAllByPkbItemIdOrderByCreatedAtAsc(UUID pkbItemId);

    List<PkbProvenanceEntity> findAllByWorkflowIdOrderByCreatedAtAsc(String workflowId);
}
