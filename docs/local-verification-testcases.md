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
| AUTO-004 | CI deployment scripts         | `./gradlew testCiScripts --no-daemon`                                                                                                         | Classifier allows image-only Helm diffs without approval regardless of changed source files, requires approval for non-image Helm and suppressed Secret changes, writes a manual approval review with the full Helm diff, and verifies all Helm paths inject database and S3 credentials from GitHub Secrets using temporary files. |
| AUTO-005 | PKB artifact storage integration tests | `./gradlew artifactStorageTest --no-daemon` | Deterministic object-key generation, artifact registration validation, object-reference persistence, artifact provenance, consent binding, and enrichment event behavior pass. |
| AUTO-006 | Local service E2E suite       | With local dependencies and the service running, execute `./gradlew e2eTest --no-daemon`                                                       | Black-box HTTP verification passes against the running local service, seeded PostgreSQL data, temporary BioCompass auth introspection stub, local AU decision endpoint, and Kafka consumer. |
| AUTO-007 | Kafka command ingress tests   | `./gradlew kafkaIngressTest --no-daemon` | Real Kafka/PostgreSQL tests cover broker-acknowledged JSON submission, fresh AU enforcement, exact deduplication, mismatch detection, transient AU/database retry, immediate permanent-AU failure handling, exhaustion and malformed input to DLT, partition progress, and replay. |
| AUTO-008 | PKB command HTTP API tests    | `./gradlew commandApiTest --no-daemon` | MockMvc exercises all command endpoints, header handling, authorization, validation and Kafka-publication error responses through Spring-managed controllers and MapStruct mappers. |
| AUTO-009 | PKB AU integration tests      | `./gradlew test --tests 'com.biocompass.pkb.authorization.*' --no-daemon` | The real AU HTTP client sends safe actor/service context, service credentials, and prior decision reference; enforcement fails closed for denial, invalid binding, obligations, and unavailability. |

## Local Service Testcases

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
| LOCAL-013 | PKB item search                      | Covered by `./gradlew e2eTest --no-daemon`, which seeds matching and non-matching `pkb_item` rows and calls `GET /api/pkb/items?...` with entity, subtype, status, time, privacy, text, limit, and offset filters. | Response contains only matching items for the authenticated user scope in the expected order.         |
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

## Feature Testcase Backlog

Add concrete local service testcases here as features land:

- Item create/update/delete HTTP flows.
- Artifact object upload/download HTTP flows after metadata registration.
- FHIR mapping and resource retrieval behavior.
- PKB item `observed_timezone` acceptance and rejection: a command with `observed_at` and a valid IANA identifier is accepted and returned, an invalid identifier or a bare offset is rejected, and `observed_timezone` without `observed_at` is rejected.
