# Bounded generic payload contract and implementation plan

**Status:** Proposed for contract review only; not implemented or approved for implementation.
**Issue:** [PKB #62](https://github.com/Bio-Compass/pkb-service/issues/62)
**Reviewed:** 2026-11-14
**Owners:** PKB owns fact storage and transport validation; the producing service owns its domain model.

The [central companion](https://github.com/Bio-Compass/bio-compass-architecture-docs/blob/codex/pkb-62-client-neutral-integration/docs/decisions/project-decision-log.md#pkb62-payload-follow-up)
records the proposed cross-service boundary. This document owns the candidate wire contract,
limits and execution plan. Approval to prepare this proposal authorizes documentation only.
The [current source-record contract](pkb_record_api.md) continues to describe implemented behavior.

**Additive implementation, 2026-11-15:** the [current API](pkb_record_api.md#raw-json-extradata)
keeps required `quantity` and supports bounded optional raw JSON `extraData`. That step does not
implement this proposal's full `payload` replacement, async-wide budgets, registry or history byte cap.

## Problem and inspected baseline

At [PKB revision ddea92a](https://github.com/Bio-Compass/pkb-service/tree/ddea92a99c863b0099ddbabd833ba14cafec1685),
the synchronous create DTO requires `quantity`; persistence constructs only `{amount, unit}`.
Its row mapping, responses, replay comparison and aggregates also assume that shape. The
asynchronous create command already accepts an object payload, but has no shared explicit
payload-budget contract with the synchronous API. Changing the INSERT alone would lose data
in responses or leave reads and replay checks dependent on quantity fields.

[BWTS main ac571b0](https://github.com/Bio-Compass/biocompass-watertracker-service/blob/ac571b03c18887e50fd17ee50d64851beecd41fd/app/domain/hydration.py)
includes immutable beverage/accounting snapshots. The water-only PKB adapter at
[BWTS revision 4b6d5a0](https://github.com/Bio-Compass/biocompass-watertracker-service/tree/4b6d5a0)
does not carry them. Composition, recipes and nutritional properties are motivating future
examples, not capabilities established by this inspection.

## Envelope, ownership and completion

Both APIs carry the same meaning and technical constraints for `payload`. Their existing envelope
field names, command metadata and authentication contracts remain transport-specific. This does
not introduce a third API or automatically reconcile their other lifecycle capabilities.

- The synchronous API returns read results and commit-confirmed writes.
- The asynchronous API acknowledges an authorized command only after Kafka acknowledgement.
  Its consumer obtains a fresh AU decision and validates the payload again before persistence.
- Producer, verified owner, provenance, times, privacy/consent and lifecycle remain explicit
  envelope attributes. A payload field never overrides BAS identity, workload identity or an AU
  decision. User and staff tokens alone cannot call either API.
- PKB validates JSON structure and resource budgets. BWTS validates beverage meaning and preserves
  an immutable snapshot for an intake. Catalogue definitions remain BWTS-owned; the personal
  observation and its recorded snapshot belong in PKB.
- Large documents and binary artifacts retain the accepted object-storage boundary. JSON strings
  do not create an inline-file API. Payload data, credentials and authority tokens are not logged;
  authentication headers/context are never copied into payloads, Kafka messages or outbox events.

## Candidate synchronous wire change

Replace the required top-level `quantity` with required `payload`, whose root must be a JSON
object. A non-quantity fact needs no amount or unit. Unknown fields inside that object are retained;
unknown envelope fields continue to be rejected. The candidate request keeps the other existing
source-record fields and header requirements. Example payload fragment, not a complete request:

```json
{
  "entity_type": "nutrition_intake",
  "subtype": "water",
  "payload": {
    "amount": 250,
    "unit": "mL",
    "beverage_snapshot": {
      "name": "Water",
      "properties": {"example_flag": true},
      "components": [],
      "note": null
    }
  }
}
```

The snapshot above illustrates nested JSON only. It does not define a beverage schema or permit
other drinks to be classified as water. BWTS must define its actual snapshot mapping separately.
Do not add `schema_id`, `schema_version` or a registry lookup until that contract is agreed.

Create/replay, lookup and history responses return the complete stored `payload`, including
unknown nested fields. Remove the synchronous quantity-only representation together with its
BWTS client mapping. The user confirmed there are no existing PKB clients to preserve; do not add
a dual-input compatibility mode. The planned BWTS adapter and reusable verification assets still
need a coordinated update before this contract can be enabled.

JSONB preserves semantic JSON content, not original bytes, whitespace, key order or numeric
spelling. Decimal values must remain exact through HTTP, persistence, Kafka and reads, using
integer/decimal representations without an intermediate binary floating-point conversion.
Declared source precision or formatting, if meaningful, needs explicit domain data.

## Proposed technical budgets

These are review candidates, not implemented defaults or accepted platform limits. Apply one
versioned validation policy to both APIs, including async consumer-time validation. Centralize the
policy without duplicating literals across validators, serializers, ingress and tests.

| Boundary | Proposed maximum and counting rule |
| --- | --- |
| HTTP request body | 128 KiB of actual UTF-8 body bytes, including the envelope; enforce while reading, including chunked requests |
| Serialized Kafka command | 256 KiB including verified context; check before publication and before deserialization on consumption |
| Payload size | 64 KiB of compact JSON encoded as UTF-8 with exact, normalized decimal values |
| Whole-message nesting | 20 object/array levels including the HTTP or Kafka envelope |
| Payload nesting | 16 object/array levels; root object is level 1; primitive leaves add no level |
| Total payload nodes | 2,048 values, counting every object, array and primitive, including the root; member names are not nodes |
| Object members | 128 per object |
| Array entries | 512 per array |
| Member name | 256 decoded UTF-8 bytes |
| String value | 8 KiB of decoded UTF-8 bytes |
| Number | 128 characters in its token, decimal precision at most 64 and scale from -64 through 64; reject rather than round |
| History response | 2 MiB encoded body and at most the requested row limit, capped at the existing 500; complete traversal uses cursors |

Nested objects, arrays, strings, exact JSON numbers, booleans and null are permitted. A root array,
scalar or null is rejected. Reject malformed UTF-8, unpaired surrogates, U+0000, duplicate member
names after escape decoding, non-JSON numeric literals and trailing tokens. Preserve Unicode
content; do not case-fold names or normalize strings to change their meaning. Fields such as
`@type` and `$ref` are data: do not enable runtime polymorphic typing or remote reference fetching.

Use parser byte/depth/token guards before allocating a complete tree; validation after building
an unbounded object graph is insufficient. The whole-message depth budget includes the
payload wrapper and command context as well as the payload-specific depth limit. Initially accept uncompressed JSON requests; compressed input
requires its own decompressed-size policy and is outside this proposal. Do not trust Content-Length
as the only byte count. Transport-level size rejection may precede application authentication;
within PKB, preserve workload rejection before body deserialization and protected downstream work.

History must stop at the last item that fits, return its cursor and continue without skipping
items. Do not load an entire worst-case 500 x 64 KiB page to enforce the smaller response budget.
Authorize each returned item; never skip denied or oversized facts to produce a successful page.
A single historical fact exceeding the response budget causes a bounded error, not truncation.
Audit retained data and resolve incompatible records under owner-approved handling before activation.

## Persistence, replay and lifecycle

Store the supplied payload as bound JSONB. General row reads and response mapping must not cast
`payload.amount` or assume it exists. Item, provenance, idempotency state and outbox remain atomic.
Do not add full payloads to events that currently contain only identifiers and safe metadata.

Retain the source `fingerprint` for BWTS intent and import compatibility. Independently compare the
entire stored payload for a source-record replay; a client-supplied matching fingerprint alone is
insufficient. Use exact JSONB structural equality under the same transaction or an equivalent
exact-decimal comparison. Do not add another persisted content hash merely to replace equality.

- Object key order and numeric spellings such as `250` / `250.0` do not create a conflict.
- Array order, missing versus null, JSON type, or any nested value change does create a conflict.
- Existing stable category, acquisition and governance comparisons remain enforced.
- Existing source-record replay rules retain the original candidate identity and defaulted times.
  BWTS's source fingerprint must still distinguish changes to explicit user intent, including an
  explicitly supplied observation time. This proposal does not redefine general command deduplication.
- Equivalent replay returns the complete original item without a second creation event and still
  reauthorizes disclosure. Changed payload with the same key is `409 idempotency_conflict` even if
  the producer reuses its fingerprint. Deleted keys remain retired until the explicit retention purge.

A new representation must not overwrite, discard or invent existing payload fields. Retained data
outside the proposed budgets needs an explicit inventory and remediation decision; no startup
rewrite, destructive cleanup or unconditional bypass is planned.

## Quantity operations are an explicit projection

Generic payload storage does not make arbitrary JSON fields aggregatable. For this bounded slice,
the existing quantity operations project root `payload.amount` and `payload.unit` for an explicitly
requested owner/producer/category/unit/window selection. Keep the current positive exact scalar
`mL` profile, with at most 30 integer and 12 fractional digits. This does not reserve those names
for all facts or restrict unrelated payloads to that profile.

A fact without the requested unit is not a quantity candidate. Every candidate matching that unit
must have a JSON number amount conforming to the profile. A missing, string, null, malformed or
out-of-range amount rejects the whole aggregate after authorization; it is never coerced to zero,
cast from a string, or silently excluded. Guard SQL conversion explicitly; do not rely on predicate
evaluation order to avoid a cast failure. General lookup/history still returns such a generic fact.

Preserve every contributing UUID's AU check, complete candidate pages and the calculation's
REPEATABLE READ snapshot. Raw reads and aggregates continue to require `hydration:intake:read`
in the BWTS slice, with existing BAS grants unchanged. No implicit nutritional, recipe or hydration
coefficient calculation belongs in PKB. Conversions, ranges, other units and the long-term quantity
placement remain in [issue #40](https://github.com/Bio-Compass/pkb-service/issues/40).

## Failure contract

Preserve service/user authentication and AU failure semantics and each transport's existing safe
error envelope. Share validation rules rather than inventing another authentication/error plane.

| Condition | Candidate behavior |
| --- | --- |
| Actual HTTP body, payload size or outgoing command size exceeded | HTTP `413` with bounded `payload_too_large`; no persistence/publication |
| Invalid payload shape, depth, nodes, strings or numbers | Sync `422 invalid_request`; async `400 pkb_invalid_command`; no raw data in details |
| Malformed JSON / duplicate names / invalid encoding | Existing transport parser error status (`422` sync, `400` async), with bounded details |
| Invalid quantity candidate during an authorized aggregate | Sync `422 invalid_request`, whole result rejected |
| Unsupported Content-Encoding | HTTP `415` with bounded `unsupported_media_type`; no decompression |
| Retained historical item cannot fit a read response | Sync `413 payload_too_large`, no partial item |
| Invalid command received through Kafka | Permanent validation failure through existing command DLT handling; no canonical write/event |

Consumer admission must not silently discard an oversized message. Keep it recoverable through the
existing command failure path with bounded safe context. Check broker/topic limits and DLT
headroom together before activation so original invalid messages remain recoverable under the
existing recovery contract. An application byte cap does not replace broker admission controls.
Payload/profile errors are not transient
BAS/AU outages. Keep BWTS's public errors and storage-unavailable mapping; do not automatically fall
back to its local database. Errors and logs contain neither field values nor raw parser excerpts.

## Registry boundary and deferred domain work

The registry needs a separate proposal: authenticated producer-to-standard/schema bindings, schema
ownership, immutable versions, compatibility, validation failure behavior, and availability/security
rules. It need not be a separately deployed service. Do not let a producer self-authorize a schema,
fetch arbitrary schemas/URLs or execute validators from a payload.

Until that decision, the producing service owns domain validation and PKB enforces the reviewed
technical contract and AU resource controls. A future registry must not reinterpret or silently
rewrite already stored facts. This proposal adds no registry implementation or placeholder fields.

Full beverage integration is also separate from changing a wire field. BWTS must define complete
mapping for intent format, beverage identity/origin/category/name/locale, coefficient and credit
source/rule version before activation. Import must preserve those snapshots for active and deleted
records. Recipes/nutrition need a concrete BWTS model first. Existing goal-history semantics also
need their own compatible storage decision; generic intake JSON does not solve goal storage.
The current water-only importer must never accept typed drinks and silently discard their fields.

## Implementation sequence after contract approval

The stages below are planned work, not implementation or validation evidence.

1. **Review gate.** Approve payload shape, budgets/counting, error mapping, replay semantics,
   quantity eligibility and staged BWTS scope. Inventory existing payloads. Keep this document and
   the central companion proposed until their respective owners approve them.
2. **Shared payload boundary.** Select the existing JSON library extension points for streaming
   constraints, exact numbers and a reusable validator/codec. Cover HTTP, JSONB read/write and
   Kafka producer/consumer parsing. Ensure valid input stays exact through every boundary.
3. **Synchronous pipeline.** Replace quantity-only DTO/response/store mapping with full payload;
   compare complete JSON on replay; retain atomic outbox/provenance/keys and commit-confirmed
   success. Add byte-bounded history pagination. Verify non-quantity facts through create, read and delete.
4. **Async and quantity parity.** Apply the shared policy before command publication and at the
   consumer without changing acknowledgements. Implement guarded quantity extraction and
   whole-result failure for invalid candidates. Keep AU binding and per-item checks intact.
5. **BWTS wire alignment.** Update the currently planned water adapter, response schema and manual
   assets atomically with PKB. Preserve its integer bounds, intent fingerprints, public errors and
   BUPS/DST behavior. Keep typed beverages/goals unenabled until the separate domain contract and
   mapping/import work is approved and verified; reject incompatible activation/import explicitly.
6. **Persistence/import audit.** Existing `{amount, unit}` JSONB remains valid input data; no rewrite
   or new schema is justified solely by accepting JSON. Evaluate constraints/indexes and any
   necessary migration explicitly. Verify complete payloads, IDs, times, fingerprints, deleted keys
   and daily totals on repeated paused-write imports. Retain the source and forward-recovery rule.
7. **Verification and publication.** Run focused unit/context/PostgreSQL/Kafka/API checks followed
   by PKB full verification, affected BWTS/BAS checks and both OpenAPI drift checks. Repeat full-diff
   CodeRabbit review after confirmed fixes. No deployment, secret provisioning or live cutover is
   authorized by this planning step.

## Required acceptance evidence for that implementation

- Boundary cases at each budget: below, exactly at and above; chunked/incorrect Content-Length;
  duplicate escaped keys; deep/wide trees; invalid Unicode; huge exponents and precise decimals.
- Root and nested JSON type coverage, complete round trips, unknown-field preservation, exact
  numbers after Kafka and JSONB, and equal semantics for authorized producer services irrespective
  of mobile/browser/Telegram/other originating clients.
- Non-quantity create/read/delete, bounded complete history, aggregate profile validation, no unsafe SQL casts,
  late-page AU denial and concurrent snapshot changes without partial/incorrect totals.
- Concurrent equivalent replays and changed nested payload with an unchanged source fingerprint;
  retained deletion keys and explicit retention purge; rollback leaves no outbox/provenance leak.
- Workload/user substitution, cross-purpose credentials, unsupported obligations, BAS/AU/PKB failures,
  and absence of credentials or payload values in logs/errors/events.
- Water adapter create/read/summary/statistics/delete/retry with DST and repeated import; explicit
  rejection of unsupported typed snapshots/goals. No beverage-readiness claim until their separate
  full mapping and migration cases pass.

## Decisions to resolve in review

Approve or adjust the numeric budgets and history byte cap; confirm the candidate replacement of
`quantity` and the unit-profile failure policy; agree the boundary between the initial water adapter
update and later beverage/goal support. Registry governance and the full quantity model stay separate.
No runtime changes, new dependencies, migrations or executable validators accompany this proposal.
