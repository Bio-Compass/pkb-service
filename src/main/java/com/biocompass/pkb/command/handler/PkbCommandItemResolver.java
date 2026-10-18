package com.biocompass.pkb.command.handler;

import com.biocompass.pkb.command.PkbCommandNotFoundException;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PkbCommandItemResolver {

    private final PkbItemDao itemDao;

    void requireOwnedItem(UUID userId, UUID pkbItemId) {
        if (pkbItemId == null || !itemDao.existsByUserAndItemId(userId, pkbItemId)) {
            throw new PkbCommandNotFoundException("PKB item", pkbItemId);
        }
    }

    Optional<PkbItemEntity> findOptionalOwnedItemForUpdate(UUID userId, UUID pkbItemId) {
        return pkbItemId == null
                ? Optional.empty()
                : Optional.of(itemDao.findOwnedItemForUpdate(userId, pkbItemId)
                        .orElseThrow(() -> new PkbCommandNotFoundException("PKB item", pkbItemId)));
    }
}
