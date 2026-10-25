package com.biocompass.pkb.persistence.dao;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.biocompass.pkb.persistence.entity.PkbProvenanceEntity;
import com.biocompass.pkb.persistence.repository.PkbItemRepository;
import com.biocompass.pkb.persistence.repository.PkbProvenanceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbItemDao {

    private final PkbItemRepository itemRepository;
    private final EntityManager entityManager;
    private final PkbProvenanceRepository provenanceRepository;

    @Transactional
    public PkbItemEntity save(PkbItemEntity item) {
        return itemRepository.save(item);
    }

    @Transactional
    public PkbItemEntity saveWithProvenance(PkbItemEntity item, PkbProvenanceEntity provenance) {
        var savedItem = itemRepository.save(item);
        provenance.setPkbItemId(savedItem.getPkbItemId());
        provenanceRepository.save(provenance);
        return savedItem;
    }

    @Transactional
    public PkbProvenanceEntity saveProvenance(PkbProvenanceEntity provenance) {
        return provenanceRepository.save(provenance);
    }

    @Transactional(readOnly = true)
    public Optional<PkbItemEntity> findByUserAndItemId(UUID userId, UUID pkbItemId) {
        return itemRepository.findByPkbItemIdAndUserId(pkbItemId, userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PkbItemEntity> findOwnedItemForUpdate(UUID userId, UUID pkbItemId) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Supersession requires a write transaction");
        }
        var item = itemRepository.findOwnedItemForUpdate(userId, pkbItemId);
        // A locking query can return an entity already cached in this persistence context.
        item.ifPresent(entity -> entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE));
        return item;
    }

    @Transactional(readOnly = true)
    public boolean existsByUserAndItemId(UUID userId, UUID pkbItemId) {
        return itemRepository.existsByPkbItemIdAndUserId(pkbItemId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<PkbItemEntity> findBySource(UUID userId, String sourceType, String sourceId) {
        return itemRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
    }

    @Transactional(readOnly = true)
    public List<PkbItemEntity> findAllByUser(UUID userId) {
        return itemRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<PkbItemEntity> findAllByUserAndType(UUID userId, String entityType, String subtype) {
        return itemRepository.findAllByUserIdAndEntityTypeAndSubtypeOrderByObservedAtDesc(
                userId,
                entityType,
                subtype
        );
    }

    @Transactional(readOnly = true)
    public List<PkbProvenanceEntity> findProvenance(UUID pkbItemId) {
        return provenanceRepository.findAllByPkbItemIdOrderByCreatedAtAsc(pkbItemId);
    }

    @Transactional
    public void deleteByUserAndItemId(UUID userId, UUID pkbItemId) {
        itemRepository.findByPkbItemIdAndUserId(pkbItemId, userId)
                .ifPresent(itemRepository::delete);
    }

    @Transactional
    public void deleteProvenance(UUID provenanceId) {
        provenanceRepository.deleteById(provenanceId);
    }
}
