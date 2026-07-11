package com.biocompass.pkb.query;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PkbItemQueryService {

    private final PkbItemQueryRepository repository;

    public PkbItemRecord getItem(UUID userId, UUID itemId) {
        return repository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(PkbItemNotFoundException::new);
    }

    public List<PkbItemRecord> search(PkbItemSearchCriteria criteria) {
        return repository.search(criteria);
    }
}
