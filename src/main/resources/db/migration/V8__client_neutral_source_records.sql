ALTER TABLE pkb_item
    ADD COLUMN source_system TEXT,
    ADD COLUMN source_record_id TEXT,
    ADD COLUMN recorded_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_pkb_item_source_pair CHECK (
        (source_system IS NULL AND source_record_id IS NULL)
        OR (source_system IS NOT NULL AND source_record_id IS NOT NULL
            AND length(btrim(source_system)) > 0 AND length(btrim(source_record_id)) > 0)),
    ADD CONSTRAINT ck_pkb_item_source_record_times CHECK (
        source_system IS NULL OR (recorded_at IS NOT NULL AND observed_at IS NOT NULL)),
    ADD CONSTRAINT ck_pkb_item_source_record_deleted CHECK (
        source_system IS NULL OR ((status = 'deleted') = (deleted_at IS NOT NULL))),
    ADD CONSTRAINT ck_pkb_item_source_quantity CHECK (
        source_system IS NULL OR entity_type <> 'nutrition_intake' OR subtype <> 'water'
        OR CASE WHEN jsonb_typeof(payload -> 'amount') = 'number'
            THEN (payload ->> 'amount')::numeric > 0 AND COALESCE(payload ->> 'unit' = 'mL', false)
            ELSE false END);

CREATE UNIQUE INDEX uq_pkb_item_source_record
    ON pkb_item (source_system, source_record_id)
    WHERE source_system IS NOT NULL AND source_record_id IS NOT NULL;
CREATE INDEX idx_pkb_item_source_history
    ON pkb_item (user_id, source_system, entity_type, subtype, observed_at, recorded_at, pkb_item_id)
    WHERE deleted_at IS NULL AND status = 'active' AND source_system IS NOT NULL;
CREATE INDEX idx_pkb_item_source_deleted
    ON pkb_item (source_system, user_id, deleted_at, pkb_item_id)
    WHERE deleted_at IS NOT NULL;

CREATE TABLE pkb_item_idempotency (
    producer TEXT NOT NULL,
    user_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    fingerprint TEXT NOT NULL,
    pkb_item_id UUID NOT NULL,
    PRIMARY KEY (producer, user_id, idempotency_key),
    CONSTRAINT uq_pkb_item_idempotency_item UNIQUE (pkb_item_id),
    CONSTRAINT fk_pkb_item_idempotency_owner FOREIGN KEY (pkb_item_id, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_pkb_item_idempotency_producer CHECK (length(btrim(producer)) > 0),
    CONSTRAINT ck_pkb_item_idempotency_fingerprint CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
