# Kafka Command Ingress

## Scope

The command ingress is the asynchronous half of the synchronous PKB write API:
the HTTP adapter authenticates the caller, obtains an AU decision, and returns
`202 Accepted` only after Kafka acknowledges the command. The PKB consumer then
obtains a fresh AU decision immediately before the canonical PostgreSQL write.

This transport is separate from post-commit PKB domain events. Command topics
must never be reused for `pkb.item.created`, `pkb.artifact.created`, or
enrichment events. Domain events remain the scope of GitHub issue #7 and require
a transactional outbox or equivalent before publication is enabled.

## Authorization Contract

The Kafka envelope contains only the safe context needed to re-authorize:

- configured producer service name;
- actor ID and user ID;
- staff, role, and scope attributes returned by authentication;
- purpose of use;
- the HTTP-time AU decision reference for audit correlation.

It never contains the caller's bearer token. The consumer treats the carried
decision reference as audit context, calls the BioCompass AU Service again, and
requires an allowed decision bound to the exact `write` action and target user.
Denied, unavailable, malformed, target-substituted, or unsupported-obligation
decisions fail closed.

Kafka authenticates the producer service at the broker boundary. The envelope's
`producerService` is descriptive audit context and is not a substitute for
SASL/mTLS authentication and topic ACLs.

## Topics

| Purpose | Default topic | Writer | Reader |
| --- | --- | --- | --- |
| Accepted write commands | `pkb.commands.ingress.v1` | PKB service identity | PKB command-writer group |
| Durable failures | `pkb.commands.ingress.dlt.v1` | PKB service identity | Operations/replay identity |
| Approved replay | `pkb.commands.ingress.replay.v1` | Operations/replay identity | PKB command-writer group |

The ingress, DLT, and replay topics must have the same partition count because
the dead-letter recoverer preserves the source partition. User ID is the record
key, preserving per-user ordering.

## Failure And Replay Process

Spring Kafka's `ErrorHandlingDeserializer` preserves malformed input for the
error handler. `DefaultErrorHandler` retries only transient AU and data-access
failures using bounded exponential backoff. AU HTTP 4xx responses are permanent
except for request timeout, too-early, and rate-limit responses (408, 425, and
429). Exhausted transient failures and permanent failures are written to the
DLT together with Spring Kafka's original topic, partition, offset,
consumer-group, and exception headers. An offset is not considered recovered
if the DLT publication itself fails.

Replay is deliberately manual:

1. Inspect the DLT record and its exception headers.
2. Correct the underlying outage or invalid data. A reused command ID with a
   different user, type, or payload must not be replayed under that ID.
3. Using the restricted replay service identity, copy the original key and raw
   value to `pkb.commands.ingress.replay.v1`.
4. Confirm the replay topic is consumed and either a single
   `pkb_processed_command` row is committed or a new DLT record explains the
   failure.
5. Confirm the canonical item/artifact/relationship count and consumer lag.

Replay never writes PostgreSQL directly and never skips the consumer-time AU
decision. There is no automatic DLT-to-replay loop.

## Idempotency And Audit

`X-Command-Id` identifies one immutable logical command. The inbox row stores
the command type, target user, and SHA-256 hash of canonical JSON. Only an exact
match is a retry; it increments `delivery_count` and does not invoke the command
handler again. Reuse with a different user, type, or payload throws a permanent
failure and is retained in the DLT.

The committed inbox row also records producer service, actor identity, purpose
of use, HTTP-time and consumer-time AU decision references, correlation ID, and
first/last delivery timestamps.

## Deployment Ownership

The Kubernetes profile keeps command ingress disabled by default. Setting
`PKB_KAFKA_COMMAND_INGRESS_ENABLED=true` enables both HTTP command acceptance
and the Kafka consumer, and is a rollout gate rather than a convenience flag.
The platform owner must not enable it until the AU decision URL/service
credential and the authenticated Kafka identities and ACLs below are deployed.
Until then command endpoints fail closed with `503` and no command records are
consumed. The current unauthenticated `PLAINTEXT` development-style Helm broker
does not satisfy this gate; its replacement must be tracked and delivered in
`Bio-Compass/bio-compass-helm` before command ingress is rolled out.

The PKB service repository owns message schemas, topic defaults, consumer
behavior, and this replay contract. The `Bio-Compass/bio-compass-helm`
repository's platform/Kafka deployment owner owns:

- creating all three command topics with matching partitions and production
  replication/retention settings;
- disabling reliance on broker auto-topic creation;
- issuing the PKB and replay SASL/mTLS credentials through Kubernetes Secrets;
- granting the ACLs in the table above and the command-writer group ACL;
- exposing broker security settings through standard Spring Kafka properties;
- monitoring ingress/replay lag and DLT growth.

The runtime PKB identity must not receive write access to the replay topic.
Domain-event topic provisioning and ACLs are separate from this command-ingress
contract.
