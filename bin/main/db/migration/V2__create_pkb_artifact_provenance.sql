CREATE TABLE pkb_artifact_provenance (
    artifact_provenance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id UUID NOT NULL,
    user_id UUID NOT NULL,

    source_kind TEXT NOT NULL,
    actor_type TEXT,
    workflow_id TEXT,
    source_reference TEXT,
    extraction_method TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pkb_artifact_provenance_artifact_owner
        FOREIGN KEY (artifact_id, user_id)
        REFERENCES pkb_artifact (artifact_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_pkb_artifact_provenance_source_kind_not_blank CHECK (length(btrim(source_kind)) > 0)
);

CREATE INDEX idx_pkb_artifact_provenance_artifact
    ON pkb_artifact_provenance (user_id, artifact_id);
CREATE INDEX idx_pkb_artifact_provenance_source_kind
    ON pkb_artifact_provenance (source_kind);
CREATE INDEX idx_pkb_artifact_provenance_workflow
    ON pkb_artifact_provenance (workflow_id)
    WHERE workflow_id IS NOT NULL;
