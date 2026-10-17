package com.biocompass.pkb.eventing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PkbOutboxEventRepository extends JpaRepository<PkbOutboxEventEntity, UUID> {

    @Query(value = """
            SELECT *
            FROM pkb_outbox_event
            WHERE published_at IS NULL
            ORDER BY occurred_at, event_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PkbOutboxEventEntity> findUnpublishedForRelay(@Param("batchSize") int batchSize);
}
