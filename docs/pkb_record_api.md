# Client-neutral canonical record API — PKB #62

Working-branch contract for [issue #62](https://github.com/Bio-Compass/pkb-service/issues/62).
The accompanying central decision remains **proposed**; this is not deployment evidence.

PKB does not classify a caller as Telegram, mobile, browser or another UI. It resolves the user
through BAS, authenticates the producer workload independently and enforces current AU decisions.
BWTS owns hydration semantics and is the first consumer of these generic record operations.

## Authentication and policy

All `/api/v1/internal/items` operations require three headers:

- `Authorization: Bearer <directed producer-to-PKB credential>`;
- `X-BioCompass-Service: <producer identity>`;
- `X-BioCompass-User-Token: <original user authority>`.

Configure inbound `biocompass.pkb.records.service-tokens` explicitly. The initial BWTS entry reads
`PKB_RECORD_BWTS_SERVICE_TOKEN`; an empty entry authorizes nobody. Unique credentials are required.
PKB calls `PKB_RECORD_AUTHORITY_URL` with its separate `PKB_RECORD_AUTHORITY_SERVICE_TOKEN` and
service identity `pkb-service`. The BAS endpoint is
`POST /api/v1/internal/auth/backend-authority/verify/`, accepting `token` and `resource_service`.
BAS allows only the configured backend/resource relationship, preserves existing audiences and
returns normalized `user_id`, `actor_id`, `resource_service`, `permissions`, `purpose_of_use`.
No token or credential is persisted, cached or placed in an event. Network isolation and a
public-edge denial for internal paths remain required deployment prerequisites.

Ownership and source system come from this verified context. Request-body or query `user_id`
is rejected. AU receives action `create`, `read`, `aggregate` or `delete`, current producer,
subject/actor, effective permissions as scopes, purpose, owner, resource identifiers and
source/category/consent/privacy attributes. Missing or incorrectly bound decisions and unsupported
obligations fail closed. History authorizes every returned item with its UUID: equal governance
attributes do not imply equal item-specific AU decisions. Aggregate reads also authorize every
contributing item with its UUID, acquisition and governance attributes. They follow all internal
pages of up to 500 items, without truncation or a governance-group shortcut. Totals inspect the exact
union of requested windows; recent quantities inspect all candidates. Authorization and calculation
use the same PostgreSQL REPEATABLE READ snapshot. Any item denial or decision failure rejects the
whole result; PKB does not return a partial total or silently omit denied facts. An empty window list
performs category authorization and returns no totals without
reading records. Deletion uses one snapshot for lookup and mutation; an absent target is an authorized
no-op and cannot delete a record created during authorization. The local Rego fixture is not a
production policy.

For this BWTS integration, AU requires `hydration:intake:read` for both raw reads and aggregates.
`aggregate` remains a separate decision/audit operation; possession of the shared permission does
not bypass item-specific policy. `hydration:summary:read` alone grants neither PKB operation.
Existing BAS token grants are unchanged. BWTS still checks its public summary permission, so a
summary-only browser or delegated token loses summary, statistics and quick-add access when BWTS
uses PKB. The existing adapter maps that PKB denial to BWTS's bounded `503` storage-unavailable
response. Its PostgreSQL backend retains its existing summary-only behavior. No fallback occurs.
Per-item AU calls make aggregate authorization work proportional to the candidate record count.

## Operations

| Method and suffix | Input and result |
| --- | --- |
| POST base | Exact source record; returns `outcome` (`created` or `replayed`) and original `item`, after commit |
| GET `/{item_id}` | One owned active producer record, or 404 |
| GET base | Required `entity_type`, `subtype`, `observed_from`, `observed_before`; optional `limit` (1–500), opaque `cursor`; returns `items`, `next_cursor` |
| POST `/quantity-totals` | `entity_type`, `subtype`, `unit`, and up to 30 `{from,before}` UTC windows; returns ordered exact decimal strings in `totals` |
| GET `/recent-quantities` | `entity_type`, `subtype`, `unit`, `limit` (1–3); returns decimal strings in `values` |
| DELETE `/{item_id}` | Non-enumerating 204 for owned, absent, foreign or already deleted target after authorization |

Source creation example (all listed fields required):

```json
{
  "item_id": "2d95b1c8-91b8-4071-b8c2-6d8837bba709",
  "source_record_id": "2d95b1c8-91b8-4071-b8c2-6d8837bba709",
  "idempotency_key": "b59a2374-5b78-4204-8108-3588d3377b21",
  "fingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "entity_type": "nutrition_intake",
  "subtype": "water",
  "quantity": {"value": "250", "unit": "mL"},
  "observed_at": "2026-10-17T12:00:00Z",
  "recorded_at": "2026-10-18T12:00:00Z",
  "source_type": "self_reported",
  "privacy_scope": ["nutrition", "health"],
  "consent_scope": []
}
```

`quantity.value` is exact decimal input, stored as a JSONB numeric `payload.amount`; output is a
decimal string. The v1 scalar operation supports positive `mL` values only, the current runtime
requirement. No conversions or approximate quantities are inferred. Public BWTS integer bounds
remain BWTS-owned. The shared envelope can still describe non-quantity facts.

Item output contains the input identity, category, quantity and times plus verified `user_id` and
`source_system`; privacy/consent are enforced without being accepted as authority. BWTS maps
`recorded_at` to its original `created_at`; PKB ingestion time remains independent.

## Updates and corrections

This API has no `PUT`, `PATCH`, replacement or supersession operation. BWTS consequently cannot
edit an existing intake through the PKB adapter. For an active record, a changed request with the
original idempotency key returns `409 idempotency_conflict` rather than changing the stored record.
After soft deletion, any request with its retained key returns `409 key_retired`; soft deletion
also retains source identity. For example, changing 250 mL to 300 mL is not an implemented BWTS
operation in this slice.

The separate legacy command API supports history-preserving supersession, as described in
[the service architecture](biocompass_pkb_architecture.md#updates-and-corrections). That capability
is not exposed by this source-record contract or the BWTS adapter. A future intake-edit workflow
requires a separately defined correction and authorization contract.

## Ordering, lifecycle and retries

All reads are owner/producer scoped and exclude deleted/non-active facts. UTC windows are lower
inclusive, upper exclusive; BWTS retains time-zone, DST, target and statistics semantics. History
orders by observed time, source recording time, UUID. Consumers follow all cursors; cursor values
never confer authority. Recent quantities order by maximum observed time, maximum recorded time,
then amount descending, matching BWTS.

Unique `(source_system,source_record_id)` identifies the source record. Separate
`(producer,user_id,idempotency_key)` identifies replay semantics. Equal fingerprints and unchanged
amount/unit/category/acquisition/governance replay the original identity and defaulted times.
Server-generated candidate IDs and times are not compared. Conflicting reuse is 409
`idempotency_conflict`; retained deleted keys are 409 `key_retired`; conflicting source IDs are
409 `source_conflict`. PostgreSQL serialization, uniqueness and a single transaction cover item,
provenance, key state and creation outbox. Failed writes emit nothing; replays add no event.

Soft deletion sets canonical `deleted_at` and status together. Explicit, bounded offline purge
releases the key only after the BWTS retention period (default 90 days). No startup purge, scheduler
or public purge endpoint exists. Canonical references block purge; dependency cleanup and key
release are atomic. Historical import preserves deleted records and retained keys.

Other safe errors are 422 `invalid_request`, 401 `unauthorized`, 403 `forbidden`, 404 `not_found`,
503 `unavailable`. Upstream error text is never returned. Every response is `no-store`.
If a commit succeeds but the response is lost, retry the original idempotency key/fingerprint.
BWTS never silently falls back to its former database.

## Transition

Use BWTS's offline maintenance importer with writes paused, explicit source/target databases and
operator authority. It preserves IDs, exact values, original times, provenance and idempotency state;
repeat only identical imports. Compare complete records, active/deleted counts and daily totals
before selecting the PKB backend. Keep source data intact. Once new writes reach PKB, repair forward;
there is no reverse transfer or automatic rollback to stale local data.

## Verification

Run the PKB full verification workflow and the BWTS/BAS gates. The live local integration harness
uses real PKB HTTP and PostgreSQL with BWTS's real ASGI application and explicitly labelled BAS,
BUPS and AU contract doubles. It does not prove production BAS/AU, deployment or real-user authority.
