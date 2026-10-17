CREATE TABLE pkb_processed_command (
    command_id UUID PRIMARY KEY,
    command_type TEXT NOT NULL,
    user_id UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    producer_service TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    actor_user_id UUID NOT NULL,
    purpose_of_use TEXT NOT NULL,
    prior_decision_reference TEXT,
    applied_decision_reference TEXT NOT NULL,
    correlation_id TEXT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivery_count INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT ck_pkb_processed_command_type_not_blank
        CHECK (length(btrim(command_type)) > 0),
    CONSTRAINT ck_pkb_processed_command_hash
        CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_pkb_processed_command_producer_not_blank
        CHECK (length(btrim(producer_service)) > 0),
    CONSTRAINT ck_pkb_processed_command_actor_not_blank
        CHECK (length(btrim(actor_id)) > 0),
    CONSTRAINT ck_pkb_processed_command_purpose_not_blank
        CHECK (length(btrim(purpose_of_use)) > 0),
    CONSTRAINT ck_pkb_processed_command_decision_not_blank
        CHECK (length(btrim(applied_decision_reference)) > 0),
    CONSTRAINT ck_pkb_processed_command_delivery_count
        CHECK (delivery_count > 0)
);

CREATE INDEX idx_pkb_processed_command_user_processed
    ON pkb_processed_command (user_id, processed_at DESC);
