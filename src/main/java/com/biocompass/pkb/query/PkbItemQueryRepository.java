package com.biocompass.pkb.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PkbItemQueryRepository {

    Optional<PkbItemRecord> findByUserIdAndItemId(UUID userId, UUID itemId);

    List<PkbItemRecord> search(PkbItemSearchCriteria criteria);
}
