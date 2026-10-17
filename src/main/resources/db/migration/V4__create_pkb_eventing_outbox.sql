CREATE TABLE pkb_outbox_event (
    event_id UUID PRIMARY KEY,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    user_id UUID NOT NULL,
    pkb_item_id UUID,
    related_pkb_item_id UUID,
    artifact_id UUID,
    relationship_id UUID,
    correlation_id TEXT,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_pkb_outbox_event_type_not_blank CHECK (length(btrim(event_type)) > 0),
    CONSTRAINT ck_pkb_outbox_event_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_pkb_outbox_event_publish_attempts_non_negative CHECK (publish_attempts >= 0)
);

CREATE INDEX idx_pkb_outbox_event_unpublished
    ON pkb_outbox_event (occurred_at, event_id)
    WHERE published_at IS NULL;

CREATE TABLE pkb_processed_event (
    event_id UUID NOT NULL,
    consumer_name TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pkb_processed_event_pkey PRIMARY KEY (event_id, consumer_name),
    CONSTRAINT ck_pkb_processed_event_consumer_name_not_blank CHECK (length(btrim(consumer_name)) > 0)
);
