# PKB Canonical PostgreSQL Schema

This document describes the canonical PKB tables created by
`src/main/resources/db/migration/V1__create_pkb_schema.sql`.

The schema is the PostgreSQL source of truth for PKB records. Application DTOs,
FHIR resources, search projections, event payloads, and object storage metadata
should map back to these tables instead of becoming independent persistence
models.

## Extensions

| Extension | Responsibility |
| --- | --- |
| `pgcrypto` | Provides `gen_random_uuid()` for database-generated UUID defaults. |
| `vector` | Provides the `vector(1536)` column type and vector index support for embeddings. |

## `pkb_item`

Canonical user-owned PKB record. This table stores the normalized item envelope
and the JSONB payload for facts, observations, goals, questionnaire answers,
derived facts, and other PKB entities.

| Field | Responsibility |
| --- | --- |
| `pkb_item_id` | Stable item identifier. Primary key for direct lookup and foreign-key references. |
| `user_id` | Owner of the item. Used for user-scoped queries and ownership enforcement across related tables. |
| `entity_type` | Broad semantic category, such as observation, condition, goal, document, or questionnaire response. |
| `subtype` | More specific type inside the entity category. Supports filtering without inspecting the JSON payload. |
| `status` | Lifecycle state for the item, such as active, inactive, draft, superseded, or deleted. |
| `payload` | Canonical structured item body as JSONB. Stores domain-specific data that does not belong in the shared envelope. |
| `source_type` | Kind of source that produced the item, such as manual entry, FHIR import, OCR, enrichment, or device sync. |
| `source_id` | Optional source-system identifier for correlation, deduplication, and provenance tracing. |
| `observed_at` | Time the represented fact or event was observed in the real world. |
| `ingested_at` | Time the PKB service accepted the item into the canonical store. Defaults to insertion time. |
| `valid_from` | Start of the time window during which the item should be considered valid. |
| `valid_until` | End of the validity window. Must not be before `valid_from` when both are present. |
| `language` | Optional language tag for text-heavy payloads and search behavior. |
| `consent_scope` | Consent scopes attached directly to the item. Used by policy input construction and scoped queries. |
| `privacy_scope` | Privacy classifications attached to the item, such as medical, nutrition, behavioral, or sensitive. |
| `verification_status` | Trust or review state, such as unverified, patient-reported, clinician-verified, or derived. |
| `supersedes` | Optional item ID that this item replaces. Enforced to reference an item owned by the same user. |
| `superseded_by` | Optional item ID that replaces this item. Enforced to reference an item owned by the same user. |
| `created_at` | Database creation timestamp for auditability and operational ordering. |
| `updated_at` | Last database update timestamp. Must not be before `created_at`. |
| `search_document` | Generated `tsvector` built from envelope fields and payload text for PostgreSQL full-text search. |

Important constraints and indexes:

- `(pkb_item_id, user_id)` is unique so child tables can enforce same-user ownership.
- The payload must be a JSON object.
- GIN indexes support JSONB payload search, array scope filtering, and full-text search.
- User-scoped B-tree indexes support common filters by status, entity type, subtype, observed time, and validity window.

### Example: iPhone Water Intake

Water consumption tracked by an iPhone app or Apple HealthKit should be stored
as a canonical `pkb_item`, not in a separate one-off table. Each intake sample is
one item, with the shared searchable fields in the envelope and the
nutrition-specific details in `payload`.

```json
{
  "pkb_item_id": "5b7ec4b6-f2d5-4c6e-9001-5197a4e7b140",
  "user_id": "f1a8f35c-9c6a-4e47-9818-23bdfad2f7f9",
  "entity_type": "nutrition_intake",
  "subtype": "water",
  "status": "active",
  "payload": {
    "substance": "water",
    "amount": 350,
    "unit": "ml",
    "start_time": "2026-06-20T08:15:00+02:00",
    "end_time": "2026-06-20T08:15:00+02:00",
    "source_app": "iphone",
    "source_platform": "apple_healthkit"
  },
  "source_type": "healthkit",
  "source_id": "healthkit-sample-uuid",
  "observed_at": "2026-06-20T08:15:00+02:00",
  "valid_from": "2026-06-20T08:15:00+02:00",
  "valid_until": "2026-06-20T08:15:00+02:00",
  "privacy_scope": ["nutrition", "health"],
  "verification_status": "user_reported"
}
```

The matching `pkb_provenance` row records how the sample entered the PKB:

```json
{
  "pkb_item_id": "5b7ec4b6-f2d5-4c6e-9001-5197a4e7b140",
  "source_kind": "healthkit_sync",
  "actor_type": "system",
  "workflow_id": "healthkit-sync-2026-06-20",
  "source_reference": "healthkit-sample-uuid",
  "extraction_method": "healthkit-water-intake-import-v1"
}
```

This representation supports:

- Daily totals by filtering `user_id`, `entity_type = 'nutrition_intake'`,
  `subtype = 'water'`, and an `observed_at` time range, then summing
  `payload->>'amount'` after normalizing units.
- Deduplication by `source_type` and `source_id`.
- Authorization from `privacy_scope`, `consent_scope`, provenance, and purpose
  of use.
- Timeline views from `observed_at`, `valid_from`, and `valid_until`.

## `pkb_provenance`

Append-style provenance records for PKB items. This table explains how an item
was created or derived without overloading the item payload.

| Field | Responsibility |
| --- | --- |
| `provenance_id` | Stable provenance record identifier. |
| `pkb_item_id` | Item this provenance record describes. Deleted with the item. |
| `source_kind` | Required source category for the provenance event, such as manual, import, enrichment, OCR, or FHIR. |
| `actor_type` | Optional actor category responsible for the source action, such as user, clinician, system, or workflow. |
| `workflow_id` | Optional workflow or correlation identifier for enrichment and async processing chains. |
| `source_reference` | Optional external reference, URI, FHIR resource reference, file reference, or upstream system pointer. |
| `extraction_method` | Optional method used to derive the item, such as OCR engine, parser, model, or mapping pipeline. |
| `created_at` | Time the provenance record was created. |

Important constraints and indexes:

- `pkb_item_id` references `pkb_item` with cascade delete.
- `source_kind` must be nonblank.
- Indexes support item lookup, source-kind filtering, and workflow tracing.

## `pkb_relationship`

User-owned directed edges between two PKB items. Relationships are stored
separately so graph-like links can evolve without changing item payloads.

| Field | Responsibility |
| --- | --- |
| `relationship_id` | Stable relationship identifier. |
| `user_id` | Owner of the relationship and both endpoints. Used for same-user integrity checks. |
| `from_item_id` | Source item in the directed relationship. Must belong to `user_id`. |
| `to_item_id` | Target item in the directed relationship. Must belong to `user_id`. |
| `relationship_type` | Semantic link type, such as supports, contradicts, derived-from, mentions, or replaces. |
| `created_at` | Time the relationship was created. |

Important constraints and indexes:

- Both endpoints must belong to the same user as the relationship.
- Self-relationships are rejected.
- Duplicate `(user_id, from_item_id, to_item_id, relationship_type)` rows are rejected.
- Indexes support relationship traversal by endpoint and user-scoped filtering by relationship type.

## `pkb_artifact`

Metadata for binary or large external artifacts stored outside PostgreSQL in
S3-compatible object storage. PostgreSQL stores references and governance
metadata; the binary bytes stay in object storage.

| Field | Responsibility |
| --- | --- |
| `artifact_id` | Stable artifact identifier. |
| `user_id` | Owner of the artifact. Used for user-scoped lookup and child-table ownership checks. |
| `pkb_item_id` | Optional PKB item this artifact is associated with. Must belong to the same user. |
| `object_key` | Object storage key, such as `users/{user_id}/documents/{document_id}/original.pdf`. |
| `content_type` | Optional MIME type for the stored object. |
| `sha256` | Optional 64-character SHA-256 hex digest for integrity checks and deduplication. |
| `size_bytes` | Optional object size in bytes. Must be nonnegative when present. |
| `created_at` | Time the artifact metadata was registered. |

Important constraints and indexes:

- `(artifact_id, user_id)` is unique so consent bindings can enforce same-user ownership.
- `(user_id, object_key)` is unique to avoid duplicate object references for the same user.
- `pkb_item_id`, when present, must reference an item owned by the same user.
- Indexes support user/item lookup, creation-time listing, and hash lookup.

## `pkb_consent_binding`

Consent references attached to exactly one governed PKB subject: either an item
or an artifact. This keeps consent linkage explicit and policy-queryable.

| Field | Responsibility |
| --- | --- |
| `consent_binding_id` | Stable consent binding identifier. |
| `user_id` | Owner of the consent binding and the referenced item or artifact. |
| `pkb_item_id` | Optional item subject for this consent binding. Must belong to `user_id`. |
| `artifact_id` | Optional artifact subject for this consent binding. Must belong to `user_id`. |
| `consent_reference` | Required reference to the controlling consent record, document, or external consent source. |
| `consent_scope` | Scopes granted or constrained by this consent binding. Used in authorization decisions. |
| `policy_reference` | Optional policy, rule set, or version that interprets the consent binding. |
| `purpose_of_use` | Optional intended use context, such as treatment, operations, research, or user-requested export. |
| `valid_from` | Optional start of the consent binding validity window. |
| `valid_until` | Optional end of the consent binding validity window. Must not be before `valid_from` when both are present. |
| `created_at` | Time the consent binding was recorded. |

Important constraints and indexes:

- Exactly one of `pkb_item_id` or `artifact_id` must be present.
- Referenced items and artifacts must belong to the same user as the binding.
- `consent_reference` must be nonblank.
- GIN and B-tree indexes support consent scope lookup and time-window filtering.

## `pkb_fact_embedding`

Vector representation for one PKB item. This table is separated from `pkb_item`
so embedding generation and future vector-search behavior can evolve without
changing the canonical item envelope.

| Field | Responsibility |
| --- | --- |
| `pkb_item_id` | Item represented by the embedding. Primary key and foreign key to `pkb_item`. |
| `user_id` | Owner of the embedding and referenced item. Used for same-user integrity and search scoping. |
| `embedding` | 1536-dimensional vector used for semantic search and similarity retrieval. |
| `embedding_model` | Optional model or embedding pipeline identifier that produced the vector. |
| `created_at` | Time the embedding row was created. |
| `updated_at` | Time the embedding row was last refreshed. Must not be before `created_at`. |

Important constraints and indexes:

- Each PKB item can have at most one current embedding row.
- Embeddings are cascade-deleted with their source item.
- The HNSW cosine index supports approximate nearest-neighbor vector lookup.
- The user index supports user-scoped semantic search.
