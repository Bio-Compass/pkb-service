package com.biocompass.pkb.eventing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PkbProcessedEventDao {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean markProcessedIfNew(UUID eventId, String consumerName) {
        return jdbcTemplate.update("""
                INSERT INTO pkb_processed_event (event_id, consumer_name)
                VALUES (?, ?)
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """, eventId, consumerName) == 1;
    }
}
