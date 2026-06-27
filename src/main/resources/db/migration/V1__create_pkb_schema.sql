CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE pkb_item (
    pkb_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,

    entity_type TEXT NOT NULL,
    subtype TEXT NOT NULL,
    status TEXT NOT NULL,

    payload JSONB NOT NULL,

    source_type TEXT NOT NULL,
    source_id TEXT,

    observed_at TIMESTAMPTZ,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,

    language TEXT,

    consent_scope TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    privacy_scope TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],

    verification_status TEXT,

    supersedes UUID,
    superseded_by UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    search_document TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('pg_catalog.simple', coalesce(entity_type, '')), 'A') ||
        setweight(to_tsvector('pg_catalog.simple', coalesce(subtype, '')), 'A') ||
        setweight(to_tsvector('pg_catalog.simple', coalesce(status, '')), 'B') ||
        setweight(to_tsvector('pg_catalog.simple', coalesce(source_type, '')), 'C') ||
        setweight(to_tsvector('pg_catalog.simple', coalesce(source_id, '')), 'C') ||
        setweight(to_tsvector('pg_catalog.simple', coalesce(verification_status, '')), 'C') ||
        setweight(to_tsvector('pg_catalog.simple', coalesce(payload::TEXT, '')), 'D')
    ) STORED,

    CONSTRAINT uq_pkb_item_id_user_id UNIQUE (pkb_item_id, user_id),
    CONSTRAINT fk_pkb_item_supersedes_owner
        FOREIGN KEY (supersedes, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id),
    CONSTRAINT fk_pkb_item_superseded_by_owner
        FOREIGN KEY (superseded_by, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id),
    CONSTRAINT ck_pkb_item_entity_type_not_blank CHECK (length(btrim(entity_type)) > 0),
    CONSTRAINT ck_pkb_item_subtype_not_blank CHECK (length(btrim(subtype)) > 0),
    CONSTRAINT ck_pkb_item_status_not_blank CHECK (length(btrim(status)) > 0),
    CONSTRAINT ck_pkb_item_source_type_not_blank CHECK (length(btrim(source_type)) > 0),
    CONSTRAINT ck_pkb_item_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_pkb_item_validity_window CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),
    CONSTRAINT ck_pkb_item_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_pkb_item_supersedes_not_self CHECK (supersedes IS NULL OR supersedes <> pkb_item_id),
    CONSTRAINT ck_pkb_item_superseded_by_not_self CHECK (superseded_by IS NULL OR superseded_by <> pkb_item_id)
);

CREATE TABLE pkb_provenance (
    provenance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pkb_item_id UUID NOT NULL,

    source_kind TEXT NOT NULL,
    actor_type TEXT,
    workflow_id TEXT,

    source_reference TEXT,
    extraction_method TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pkb_provenance_item
        FOREIGN KEY (pkb_item_id)
        REFERENCES pkb_item (pkb_item_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_pkb_provenance_source_kind_not_blank CHECK (length(btrim(source_kind)) > 0)
);

CREATE TABLE pkb_relationship (
    relationship_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,

    from_item_id UUID NOT NULL,
    to_item_id UUID NOT NULL,

    relationship_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pkb_relationship_user_items_type UNIQUE (user_id, from_item_id, to_item_id, relationship_type),
    CONSTRAINT fk_pkb_relationship_from_item_owner
        FOREIGN KEY (from_item_id, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pkb_relationship_to_item_owner
        FOREIGN KEY (to_item_id, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_pkb_relationship_not_self CHECK (from_item_id <> to_item_id),
    CONSTRAINT ck_pkb_relationship_type_not_blank CHECK (length(btrim(relationship_type)) > 0)
);

CREATE TABLE pkb_artifact (
    artifact_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    pkb_item_id UUID,

    object_key TEXT NOT NULL,
    content_type TEXT,
    sha256 TEXT,

    size_bytes BIGINT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pkb_artifact_id_user_id UNIQUE (artifact_id, user_id),
    CONSTRAINT uq_pkb_artifact_user_object_key UNIQUE (user_id, object_key),
    CONSTRAINT fk_pkb_artifact_item_owner
        FOREIGN KEY (pkb_item_id, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id),
    CONSTRAINT ck_pkb_artifact_object_key_not_blank CHECK (length(btrim(object_key)) > 0),
    CONSTRAINT ck_pkb_artifact_content_type_not_blank CHECK (content_type IS NULL OR length(btrim(content_type)) > 0),
    CONSTRAINT ck_pkb_artifact_sha256_hex CHECK (sha256 IS NULL OR sha256 ~ '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT ck_pkb_artifact_size_non_negative CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE TABLE pkb_consent_binding (
    consent_binding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    pkb_item_id UUID,
    artifact_id UUID,

    consent_reference TEXT NOT NULL,
    consent_scope TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    policy_reference TEXT,
    purpose_of_use TEXT,

    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pkb_consent_binding_item_owner
        FOREIGN KEY (pkb_item_id, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pkb_consent_binding_artifact_owner
        FOREIGN KEY (artifact_id, user_id)
        REFERENCES pkb_artifact (artifact_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_pkb_consent_binding_subject CHECK (num_nonnulls(pkb_item_id, artifact_id) = 1),
    CONSTRAINT ck_pkb_consent_binding_reference_not_blank CHECK (length(btrim(consent_reference)) > 0),
    CONSTRAINT ck_pkb_consent_binding_validity_window CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE TABLE pkb_fact_embedding (
    pkb_item_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    embedding vector(1536) NOT NULL,

    embedding_model TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pkb_fact_embedding_item_owner
        FOREIGN KEY (pkb_item_id, user_id)
        REFERENCES pkb_item (pkb_item_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_pkb_fact_embedding_model_not_blank CHECK (embedding_model IS NULL OR length(btrim(embedding_model)) > 0),
    CONSTRAINT ck_pkb_fact_embedding_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX idx_pkb_item_user_status ON pkb_item (user_id, status);
CREATE INDEX idx_pkb_item_user_entity_type_subtype ON pkb_item (user_id, entity_type, subtype);
CREATE INDEX idx_pkb_item_user_validity ON pkb_item (user_id, valid_from, valid_until);
CREATE INDEX idx_pkb_item_user_observed_at ON pkb_item (user_id, observed_at DESC);
CREATE INDEX idx_pkb_item_source ON pkb_item (source_type, source_id) WHERE source_id IS NOT NULL;
CREATE INDEX idx_pkb_item_supersedes ON pkb_item (supersedes) WHERE supersedes IS NOT NULL;
CREATE INDEX idx_pkb_item_superseded_by ON pkb_item (superseded_by) WHERE superseded_by IS NOT NULL;
CREATE INDEX idx_pkb_item_payload_gin ON pkb_item USING GIN (payload jsonb_path_ops);
CREATE INDEX idx_pkb_item_consent_scope_gin ON pkb_item USING GIN (consent_scope);
CREATE INDEX idx_pkb_item_privacy_scope_gin ON pkb_item USING GIN (privacy_scope);
CREATE INDEX idx_pkb_item_search_document_gin ON pkb_item USING GIN (search_document);

CREATE INDEX idx_pkb_provenance_item ON pkb_provenance (pkb_item_id);
CREATE INDEX idx_pkb_provenance_source_kind ON pkb_provenance (source_kind);
CREATE INDEX idx_pkb_provenance_workflow ON pkb_provenance (workflow_id) WHERE workflow_id IS NOT NULL;

CREATE INDEX idx_pkb_relationship_user_type ON pkb_relationship (user_id, relationship_type);
CREATE INDEX idx_pkb_relationship_from_item ON pkb_relationship (from_item_id);
CREATE INDEX idx_pkb_relationship_to_item ON pkb_relationship (to_item_id);

CREATE INDEX idx_pkb_artifact_user_item ON pkb_artifact (user_id, pkb_item_id) WHERE pkb_item_id IS NOT NULL;
CREATE INDEX idx_pkb_artifact_user_created ON pkb_artifact (user_id, created_at DESC);
CREATE INDEX idx_pkb_artifact_sha256 ON pkb_artifact (sha256) WHERE sha256 IS NOT NULL;

CREATE INDEX idx_pkb_consent_binding_user_item ON pkb_consent_binding (user_id, pkb_item_id) WHERE pkb_item_id IS NOT NULL;
CREATE INDEX idx_pkb_consent_binding_user_artifact ON pkb_consent_binding (user_id, artifact_id) WHERE artifact_id IS NOT NULL;
CREATE INDEX idx_pkb_consent_binding_scope_gin ON pkb_consent_binding USING GIN (consent_scope);
CREATE INDEX idx_pkb_consent_binding_validity ON pkb_consent_binding (user_id, valid_from, valid_until);

CREATE INDEX idx_pkb_fact_embedding_user ON pkb_fact_embedding (user_id);
CREATE INDEX idx_pkb_fact_embedding_vector_hnsw ON pkb_fact_embedding USING HNSW (embedding vector_cosine_ops);
