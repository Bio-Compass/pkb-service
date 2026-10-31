# Local Verification Testcases

Use this file as the living checklist for local verification. Update it when a code change adds, removes, or changes service behavior.

## Policy

- After every code change, run the relevant automated tests.
- When a code change affects startup, configuration, persistence, infrastructure integration, or HTTP behavior, run the service locally and execute the applicable local service testcases below.
- Add focused unit tests for domain logic, mappers, validators, policy input construction, event payload construction, and service orchestration as those features are implemented.

## Automated Testcases

| ID       | Scope                         | Command                                                                                                                                       | Expected result                                                                                                                                                               |
|----------|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AUTO-001 | Full Gradle test suite        | `./gradlew test --no-daemon`                                                                                                                  | Build succeeds and all tests pass.                                                                                                                                            |
| AUTO-002 | Qodana static-analysis report | `qodana scan --save-report --results-dir .qodana/results`                                                                                     | Command exits successfully and writes `.qodana/results/qodana.sarif.json` plus `.qodana/results/report/index.html`.                                                           |
| AUTO-003 | PKB command module tests      | `./gradlew commandTest --no-daemon` | Command DTO validation, metadata normalization, transactional writes, supersession, relationship creation, artifact association, and after-commit domain event behavior pass. |
| AUTO-004 | CI deployment scripts | `./gradlew testCiScripts --no-daemon` | Classifier requires review for non-image changes; workflow checks ensure testing deploy precedes real deployed E2E, production depends on E2E success, both use one image digest/Helm commit, and production always requires manual approval. E2E prerequisites fail closed. |
| AUTO-005 | PKB artifact storage integration tests | `./gradlew artifactStorageTest --no-daemon` | Deterministic object-key generation, artifact registration validation, object-reference persistence, artifact provenance, consent binding, and enrichment event behavior pass. |
| AUTO-006 | Local service E2E suite       | With local dependencies and the service running, execute `./gradlew e2eTest --no-daemon`                                                       | Black-box HTTP verification passes against the running local service, seeded PostgreSQL data, temporary BioCompass auth introspection stub, local AU decision endpoint, and Kafka consumer. |
| AUTO-007 | Kafka command ingress tests   | `./gradlew kafkaIngressTest --no-daemon` | Real Kafka/PostgreSQL tests cover broker-acknowledged JSON submission, fresh AU enforcement, exact deduplication, mismatch detection, transient AU/database retry, immediate permanent-AU failure handling, exhaustion and malformed input to DLT, partition progress, and replay. |
| AUTO-008 | PKB command HTTP API tests    | `./gradlew commandApiTest --no-daemon` | MockMvc exercises all command endpoints, header handling, authorization, validation and Kafka-publication error responses through Spring-managed controllers and MapStruct mappers. Item create and supersession mapping preserve `observedTimezone`; HTTP creation accepts an omitted time zone or a valid IANA identifier with `observedAt`, preserves the instant, and rejects invalid identifiers, bare offsets, and time zones without `observedAt` before submission. |
| AUTO-009 | PKB AU integration tests      | `./gradlew test --tests 'com.biocompass.pkb.authorization.*' --no-daemon` | The real AU HTTP client sends safe actor/service context, service credentials, and prior decision reference; enforcement fails closed for denial, invalid binding, obligations, and unavailability. |
| AUTO-010 | MinIO image and S3 client compatibility | `./gradlew test --tests 'com.biocompass.pkb.local.LocalInfrastructureContextTest' --no-daemon` | The pinned `pgsty/minio` image starts through Testcontainers; the production S3 client creates a bucket, uploads an object, and reads its metadata. |
| AUTO-011 | Kafka eventing foundation     | `./gradlew test --tests 'com.biocompass.pkb.command.event.*' --tests 'com.biocompass.pkb.eventing.*' --no-daemon`                            | Event contracts, transactional outbox persistence, Kafka publication, and broker-backed idempotent consumption pass; command ingress and event consumers coexist with their respective payload types, and enrichment consumers start when a workflow bean is configured.                                                       |
| AUTO-012 | Canonical search filter normalization | `./gradlew test --tests 'com.biocompass.pkb.query.PkbItemSearchCriteriaTest' --tests 'com.biocompass.pkb.persistence.PkbPersistenceDaoIntegrationTest' --no-daemon` | Canonical filters lowercase independently of the default locale, preserve existing Unicode whitespace and blank handling, and find command-created records with each filter individually and combined. Text case is preserved in criteria and other users' items are excluded. |

## Local Service Testcases

Issue #35 source uniqueness is covered by `./gradlew test --tests 'com.biocompass.pkb.command.PkbCommandNormalizerTest' --tests 'com.biocompass.pkb.persistence.PkbSourceUniquenessIntegrationTest' --tests 'com.biocompass.pkb.persistence.PkbKafkaFailureHandlingIntegrationTest' --tests 'com.biocompass.pkb.persistence.PkbKafkaCommandProcessorIntegrationTest' --no-daemon`.
The checks verify fresh and empty-database upgrade migration, the partial unique index and removal of the old source index, duplicate rejection within/across users and for superseded items, case-sensitive identifiers, different source types, multiple null IDs, actual concurrent-insert blocking, and winner-only persistence. Kafka tests verify normalized duplicate sources reach the DLT without committed item, provenance, inbox, or outbox effects; exact command-ID redelivery still writes once.

Start local dependencies before running these checks:

```sh
docker compose --env-file .env up -d
```

Start the service with the local profile:

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

Run the local E2E suite after the service is healthy:

```sh
./gradlew e2eTest --no-daemon
```

The E2E suite starts a temporary BioCompass auth introspection stub on `http://localhost:8001/api/v1/internal/auth/token/introspect/`, which matches the local profile default. It reads these optional overrides: `PKB_E2E_BASE_URL`, `PKB_E2E_DATASOURCE_URL`, `PKB_E2E_DATASOURCE_USERNAME`, `PKB_E2E_DATASOURCE_PASSWORD`, `PKB_E2E_AUTH_PORT`, `PKB_E2E_INTROSPECTION_SERVICE_NAME`, and `PKB_E2E_INTROSPECTION_SERVICE_TOKEN`. If the auth port is changed, start the service with a matching `PKB_AUTH_INTROSPECTION_URL`.

| ID        | Scope                                | Command                                                                                                                                                                                                            | Expected result                                                                                       |
|-----------|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| LOCAL-001 | Compose dependency health            | `docker compose ps`                                                                                                                                                                                                | PostgreSQL, Kafka, MinIO, and OPA containers are running and healthy.                                 |
| LOCAL-002 | PostgreSQL readiness                 | `docker compose exec postgres pg_isready -U pkb -d pkb`                                                                                                                                                            | PostgreSQL accepts connections.                                                                       |
| LOCAL-003 | Flyway migration state               | `docker compose exec postgres psql -U pkb -d pkb -c "select version, success from flyway_schema_history order by installed_rank;"`                                                                                 | Latest expected migration is present with `success = t`.                                              |
| LOCAL-004 | Kafka broker reachability            | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list`                                                                                                                   | Command exits successfully.                                                                           |
| LOCAL-005 | MinIO readiness                      | `curl -fsS http://localhost:9000/minio/health/ready`                                                                                                                                                               | Command exits successfully.                                                                           |
| LOCAL-005A | OPA readiness                        | `curl -fsS http://localhost:8181/health`                                                                                                                                                                           | Command exits successfully.                                                                           |
| LOCAL-005B | OPA development policy               | `curl -fsS -X POST -H 'Content-Type: application/json' --data '{"input":{"actor":{"user_id":"user-1","roles":["user"],"scopes":[]},"action":"read","purpose":"self","resource":{"owner_user_id":"user-1","privacy_scope":"normal"}}}' http://localhost:8181/v1/data/biocompass/pkb/authz/allow` | Response contains `"result":true`.                                                                  |
| LOCAL-006 | Service startup and aggregate health | `curl -fsS http://localhost:8080/actuator/health`                                                                                                                                                                  | Response contains `"status":"UP"`.                                                                    |
| LOCAL-007 | Service liveness                     | `curl -fsS http://localhost:8080/actuator/health/liveness`                                                                                                                                                         | Response contains `"status":"UP"`.                                                                    |
| LOCAL-008 | Service readiness                    | `curl -fsS http://localhost:8080/actuator/health/readiness`                                                                                                                                                        | Response contains `"status":"UP"`.                                                                    |
| LOCAL-009 | Service info endpoint                | `curl -fsS http://localhost:8080/actuator/info`                                                                                                                                                                    | Command exits successfully.                                                                           |
| LOCAL-010 | OpenAPI document endpoint            | `curl -fsS http://localhost:8080/v3/api-docs`                                                                                                                                                                      | Response contains `"openapi"`, `/api/pkb/items`, and `bearerAuth`.                                    |
| LOCAL-011 | Swagger UI endpoint                  | `curl -fsS http://localhost:8080/swagger-ui/index.html`                                                                                                                                                            | Response contains `Swagger UI`.                                                                       |
| LOCAL-012 | PKB item lookup                      | Covered by `./gradlew e2eTest --no-daemon`, which seeds a `pkb_item` and calls `GET /api/pkb/items/{itemId}?userId=<user-id>` with an opaque Bearer token that is active at the temporary introspection stub.      | Response contains the requested `itemId`, `userId`, envelope fields, payload, scopes, and timestamps. |
| LOCAL-013 | PKB item search                      | Covered by `./gradlew e2eTest --no-daemon`, which seeds matching and non-matching `pkb_item` rows and calls `GET /api/pkb/items?...` with entity, subtype, status, time, privacy, text, limit, and offset filters. | Lowercase, mixed-case, and URL-encoded surrounding-whitespace canonical filters return identical ordered items and pages for the authenticated user scope; cross-user requests remain denied.         |
| LOCAL-014 | PKB unauthenticated query denial     | `curl -fsS -o /dev/null -w '%{http_code}' 'http://localhost:8080/api/pkb/items?userId=<user-id>'`                                                                                                                  | Response status is `401`.                                                                             |
| LOCAL-015 | PKB cross-user query denial          | Covered by `./gradlew e2eTest --no-daemon`, which authenticates as a different non-staff user.                                                                                                                     | Response code is `403`.                                                                               |
| LOCAL-016 | PKB staff cross-user query           | Covered by `./gradlew e2eTest --no-daemon`, which authenticates as a staff user.                                                                                                                                   | Response status is `200` and contains the requested user-scoped item.                                 |
| LOCAL-017 | PKB query validation                 | Covered by `./gradlew e2eTest --no-daemon`, which calls missing, malformed, and out-of-range query/path parameters with an active opaque Bearer token.                                                             | Invalid requests return `400`.                                                                        |
| LOCAL-018 | Artifact metadata registration       | After auth/API exposure and local MinIO startup, register artifact metadata for a document object key such as `users/{user_id}/documents/{document_id}/original`.                                                    | PostgreSQL contains the `pkb_artifact`, `pkb_artifact_provenance`, and optional artifact consent rows; object bytes are not stored in PostgreSQL. |
| LOCAL-019 | Kubernetes MinIO dependency          | After deploying the PKB Helm chart, run `kubectl rollout status statefulset/minio-pkb-service -n bio-compass --timeout=180s` and inspect the PKB init-container logs.                                               | MinIO is ready on the private `minio-pkb-service` Service, its `minio-data` PVC is bound, and the `initialize-minio-bucket` init container completes successfully. |
| LOCAL-020 | Kafka command ingress                | With `PKB_KAFKA_COMMAND_INGRESS_ENABLED=true`, send a valid `POST /api/pkb/commands/items?userId=<user-id>` with opaque Bearer authentication, `X-Command-Id: <UUID>`, and a valid item body. | Response is `202` only after an HTTP-time AU allow and Kafka acknowledgement; the consumer gets a fresh AU allow and atomically records the item, provenance, and inbox audit row. With the gate disabled, the endpoint fails closed with `503`. |
| LOCAL-021 | Kafka exact command retry            | Submit the identical request again with the same `X-Command-Id`, then wait until its inbox `delivery_count = 2`. | The item and `pkb_processed_command` row counts remain exactly one. |
| LOCAL-022 | Kafka command-ID mismatch            | Reuse a processed command ID with a different user, command type, or payload. | The second command makes no canonical change and reaches the DLT with an idempotency-reuse exception header. |
| LOCAL-023 | Kafka transient dependency failure   | Run `PkbKafkaFailureHandlingIntegrationTest`, which injects temporary AU failure and PostgreSQL SQLSTATE `40001`. | The accepted command is retried with backoff, eventually commits once, and is not lost. |
| LOCAL-024 | Kafka poison record                  | Run `PkbKafkaFailureHandlingIntegrationTest`, which publishes malformed raw JSON and then valid records. | Original malformed bytes reach the DLT without invoking AU or blocking subsequent consumption. |
| LOCAL-025 | Kafka retry exhaustion and replay    | Run `PkbKafkaFailureHandlingIntegrationTest`, which exhausts AU retries, observes the DLT, restores AU, and copies the original record to the replay topic. | No write occurs during failure; replay performs a fresh AU check and commits exactly once. |
| LOCAL-026 | Kafka production identity and ACLs   | Inspect the deployed broker credentials and ACLs described in [Kafka Command Ingress](kafka-command-ingress.md). | Only PKB can write ingress/DLT, only the operations identity can write replay, the PKB group can read ingress/replay, and command topics are separate from domain-event topics. |
| LOCAL-027 | Observation time-zone round trip | Covered by `./gradlew e2eTest --no-daemon`, which posts an item with `observedAt: "2026-06-20T10:15:00+02:00"` and `observedTimezone: "Europe/Amsterdam"`, waits for Kafka processing, and retrieves it through search and ID lookup. | Both read paths return `observedTimezone: "Europe/Amsterdam"`; the observed instant remains `2026-06-20T08:15:00Z`. |
| LOCAL-028 | Kafka event topics                   | With the local profile service running, execute `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list`                                                                  | The output contains the configured PKB event topics, including `pkb.item.created`, `pkb.artifact.created`, `pkb.enrichment.requested`, and `pkb.enrichment.completed`.       |
| LOCAL-029 | Kafka outbox and idempotent consumer | `./gradlew test --tests 'com.biocompass.pkb.command.event.*' --tests 'com.biocompass.pkb.eventing.*' --no-daemon`                                                                        | A committed item write leaves an unpublished outbox event with the complete payload; once an enrichment workflow is installed, Kafka delivery invokes it only once for a repeated event ID. |
| LOCAL-030 | Global source-identifier uniqueness | With the local service running, run `./gradlew e2eTest --no-daemon` and inspect `pg_indexes` for `uq_pkb_item_source`. | V7 creates a unique `(source_type, source_id)` index with `WHERE source_id IS NOT NULL` and removes `idx_pkb_item_source`. Existing HTTP creation/retry flows still return `202` and commit once; a different command with a duplicate source is a permanent consumer failure, not an upsert or synchronous HTTP conflict. |

## Supersession verification (issue #53)

- `./gradlew commandTest persistenceTest kafkaIngressTest --no-daemon`: both command forms serialize competing replacements under an owner-scoped row lock. Tests observe real PostgreSQL blocking, including a preloaded original entity. Exactly one replacement/provenance pair and its two transactional outbox events commit; the losing Kafka inbox marker rolls back. If the lock holder rolls back, the waiting command succeeds.
- `PkbSupersessionMigrationTest`: fresh databases enforce unique non-null `supersedes`, allow nulls and independent chains, and replace the old non-unique index. A V5 database with duplicate replacements fails V6 without altering records; migration succeeds after explicit manual reconciliation.
- Sequential supersession: submit two distinct command IDs to `POST /api/pkb/commands/items/{itemId}/supersessions?userId=<owner>` with valid replacement bodies. Both submissions may return `202`; only the first applies. The later command reaches DLT with `PKB item is already superseded`, without an inbox row, replacement, provenance, or domain events. Verify the original's `superseded_by` and replacement's `supersedes` are reciprocal through item lookup or PostgreSQL. Missing/foreign-owned targets fail without writes.
- Migration recovery: use the conflict query in [Canonical schema](pkb_canonical_schema.md), reconcile the history manually, then restart to rerun Flyway. Never discard a conflicting record automatically.

## Local Dependency Network Bindings (#55)

- On native Linux, check `docker version --format '{{.Server.Version}}'`.
  Require Engine 28.0.0 or later, or separately verified network restrictions
  blocking off-host access; older engines have a same-network loopback exposure
  caveat. Docker Desktop is not affected by that native-Linux issue. See
  [Local Network Access](local-development.md#local-network-access).
- Run `./gradlew test --tests 'com.biocompass.pkb.infrastructure.LocalComposePortsTest' --no-daemon`.
  The resolved PostgreSQL, Kafka, MinIO API/console, and OPA publications must all
  specify `127.0.0.1`, with both default and overridden port numbers. Missing,
  wildcard, and non-loopback bindings must fail the check. Docker CLI and Compose
  v2 are required; a running daemon is not required for this configuration check.
- Start the local stack and inspect `docker compose ps --format json` and
  `docker compose port <service> <container-port>` for all five published ports.
  Each effective host binding must be `127.0.0.1:<configured-port>`.
- Verify the dependency checks above, service health, and the E2E suite against
  those bindings. Container-network connections must remain functional.
- Use a separate Compose override for any intentional remote-development access;
  changing a port number in `.env` must never broaden the base stack's host binding.

## Feature Testcase Backlog

Add concrete local service testcases here as features land:

- Item create/update/delete HTTP flows.
- Artifact object upload/download HTTP flows after metadata registration.
- FHIR mapping and resource retrieval behavior.

## PKB #62 — client-neutral source-record integration

- Verify the real BAS Django WSGI endpoint receives length-delimited JSON with `Content-Length`,
  rather than chunked framing, from the PKB authority client. `PkbRecordApiTest` checks framing;
  exercise actual BAS login and BWTS → PKB → BAS verification in a disposable local stack.
- Verify separate producer/user credentials, invalid/expired authority, unknown producer,
  subject substitution, AU denial/unavailability, incorrect action/user binding and unsupported
  obligations. Responses/logs must not disclose upstream details or credentials.
- Verify mobile, ordinary API, browser-resource and delegated BWTS requests exercise the same
  PKB record operations; aggregate authority never permits raw-history disclosure.
- Round-trip exact mL quantities, original UUIDs/observed/recorded instants and source provenance.
- On real PostgreSQL, verify equivalent/concurrent replay, conflicting reuse, source UUID
  collisions, retained deleted keys, repeated/foreign deletion, explicit bounded purge,
  reference refusal, and atomic item/provenance/key/outbox rollback.
- Verify half-open UTC windows, 23/25-hour BWTS local days, zero-filled 7/30-day statistics,
  recent-amount tie order, owner/producer/deleted filtering, and complete cursor history.
- Run BWTS's writes-paused import twice against disposable databases; compare all identities,
  timestamps, keys, contents, active/deleted counts and daily totals. A mismatch must roll back.
- Run `manual_tests/verify_bwts_pkb.py` with the BWTS external Python environment and a built PKB
  bootJar against disposable PostgreSQL. The harness uses BAS/BUPS/AU contract doubles and must
  label that limit; it is not evidence of production authority, public-edge routing or deployment.
- Run `.agents/skills/pkb-change-verification/scripts/verify.sh full`, plus BAS/BWTS suites,
  formatting and OpenAPI drift checks. Report external blockers and skipped environments distinctly.

## Testing-to-production promotion (#66)

| ID | Check | Expected result |
| --- | --- | --- |
| CI-001 | `./gradlew testCiScripts --no-daemon` | Java workflow dependency/immutable artifact and actual classifier CLI checks pass; `./gradlew e2eConfigurationTest --no-daemon` covers deployed Java target/login failure paths and is included in `test`. |
| CI-002 | Dispatch CI with `verify-testing=true` to run the temporary Java E2E Job against internal testing service names; local replay also supports `./gradlew deployedE2eTest --no-daemon` through port forwards | Real login, scoped PKB reads, input validation, missing-item and auth-denial assertions pass; no SQL/stub/production fixtures are used. |
| CI-003 | Failed/skipped/cancelled `e2e-testing` | `deploy-prod` never runs. Missing testing secrets and BAS introspection errors are failures, not skips. |
| CI-004 | Successful test gate and production promotion review | Production uses exactly the testing image digest and pinned Helm revision, waits for manual approval, then verifies rollout/image/readiness. |
| CI-005 | Namespace credentials | Testing/prod credentials can deploy and port-forward only in their own namespace; legacy `bio-compass` credentials fail preflight. |

See the [deployment guide](kubernetes-vm-deployment.md#github-actions-testing-before-production) for prerequisites, current runtime blockers, and the companion proposed architecture patch.

### Shared Java E2E targets (#66)

- `e2eTest`: existing `PkbServiceE2eTest` in local mode, including tagged SQL fixtures, BAS stub, Kafka write/deduplication and supersession checks.
- `deployedE2eTest`: the same class in testing mode, real BAS login, shared health/OpenAPI/authentication/authorization/validation/search/missing-item checks. Tagged local fixtures are excluded and database access is rejected; no SQL account or staff account is needed.
- `e2eConfigurationTest`: namespace/loopback/credential validation, real HTTP login and verified identity handling, redirect denial and private error diagnostics. Runs as part of `test`.
- CI manual `verify-testing=true`: runs the Java suite on the existing testing release with GitHub environment secrets, uploads the Gradle report, and publishes only a test-runner image and creates a temporary Job; it skips application image publication/rollout and production promotion. A failed Java E2E gate remains a failed workflow.

The Python deployed suite was removed. Both targets reuse Java API assertions; deployed read checks do not claim the local-only write-path coverage.

### Java CI verification (#66)

`./gradlew testCiScripts --no-daemon` runs `DeploymentWorkflowTest` and `DeployChangeClassifierTest` through JUnit. These classes also run in the ordinary `test` suite; no Ruby or shell test runner is required. The classifier tests execute the actual standalone `.github/scripts/ClassifyDeployChange.java` source launcher with image-only, non-image, credential, suppressed-secret and no-change Helm diffs, then verify GitHub outputs and review artifacts. CI selects Java 25 before invoking it. Workflow/helper source files are declared Gradle test inputs so edits invalidate previous test results.

### In-cluster Java E2E Job (#66)

- `./gradlew testCiScripts e2eConfigurationTest prepareE2eRunner --no-daemon`: validates Job manifest/rendering, immutable test-runner identity, namespace and URL restrictions, lifecycle failures/timeouts, report safety and cleanup; packages the same JUnit suite for the container.
- `RunClusterE2eTest`: native Kubernetes watches replace status polling; success waits for `Complete` after `SuccessCriteriaMet` within one deadline, while failed or unexpected conditions and watch errors fail closed. Job timeout, missing/failed/skipped results and credential API errors also fail closed; foreground cleanup is attempted after all created-Job paths. The manifest has no API-token mount, public access or plaintext credentials.
- CI `verify-testing=true`: builds the test-runner from the selected source revision, runs all shared API checks over native service DNS, retrieves JUnit XML/log artifacts, and deletes the Job and its owned Secret. Application image publication/rollout and production promotion remain skipped.
- After verification, list `jobs,pods,secrets` in `testing` and confirm no resources for the CI run remain. Do not display Secret values.

## BAS introspection HTTP framing (#68)

| ID | Verification | Expected result |
| --- | --- | --- |
| AUTH-TRANSPORT-001 | `./gradlew test --tests com.biocompass.pkb.auth.BioCompassTokenIntrospectionClientTest --no-daemon` | Spring-managed client sends the exact token JSON with its UTF-8 Content-Length and no chunked transfer encoding to a real HTTP server. Active/inactive and credential rejection behavior remain covered. |

BAS on the testing cluster requires fixed-length JSON requests. Spring buffers the small introspection payload before transmission; service credentials, response parsing, and configured connect/read timeouts are preserved.
