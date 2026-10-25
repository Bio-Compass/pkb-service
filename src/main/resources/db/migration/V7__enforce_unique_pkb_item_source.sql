CREATE UNIQUE INDEX uq_pkb_item_source
    ON pkb_item (source_type, source_id)
    WHERE source_id IS NOT NULL;

DROP INDEX idx_pkb_item_source;
