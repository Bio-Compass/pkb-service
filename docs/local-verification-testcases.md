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
| AUTO-003 | PKB command module tests      | `./gradlew test --tests 'com.biocompass.pkb.command.*' --tests 'com.biocompass.pkb.persistence.PkbCommandServiceIntegrationTest' --no-daemon` | Command DTO validation, metadata normalization, transactional writes, supersession, relationship creation, artifact association, and after-commit domain event behavior pass. |
| AUTO-004 | CI deploy change classifier   | `./gradlew testCiScripts --no-daemon`                                                                                                         | Classifier allows image-only Helm diffs without approval, requires approval for service/runtime, non-image Helm, and suppressed Secret changes, and writes a manual approval review with the full Helm diff. |
| AUTO-005 | PKB artifact storage integration tests | `./gradlew test --tests 'com.biocompass.pkb.artifact.*' --tests 'com.biocompass.pkb.command.*' --tests 'com.biocompass.pkb.persistence.PkbCommandServiceIntegrationTest' --tests 'com.biocompass.pkb.persistence.PkbPersistenceDaoIntegrationTest' --tests 'com.biocompass.pkb.persistence.PkbSchemaMigrationTest' --no-daemon` | Deterministic object-key generation, artifact registration validation, object-reference persistence, artifact provenance, consent binding, and enrichment event behavior pass. |

## Local Service Testcases

Start local dependencies before running these checks:

```sh
docker compose --env-file .env up -d
```

Start the service with the local profile:

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

| ID        | Scope                                | Command                                                                                                                                                                                                                                                                                                      | Expected result                                                                                       |
|-----------|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| LOCAL-001 | Compose dependency health            | `docker compose ps`                                                                                                                                                                                                                                                                                          | PostgreSQL, Kafka, and MinIO containers are running and healthy.                                      |
| LOCAL-002 | PostgreSQL readiness                 | `docker compose exec postgres pg_isready -U pkb -d pkb`                                                                                                                                                                                                                                                      | PostgreSQL accepts connections.                                                                       |
| LOCAL-003 | Flyway migration state               | `docker compose exec postgres psql -U pkb -d pkb -c "select version, success from flyway_schema_history order by installed_rank;"`                                                                                                                                                                           | Latest expected migration is present with `success = t`.                                              |
| LOCAL-004 | Kafka broker reachability            | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list`                                                                                                                                                                                                             | Command exits successfully.                                                                           |
| LOCAL-005 | MinIO readiness                      | `curl -fsS http://localhost:9000/minio/health/ready`                                                                                                                                                                                                                                                         | Command exits successfully.                                                                           |
| LOCAL-006 | Service startup and aggregate health | `curl -fsS http://localhost:8080/actuator/health`                                                                                                                                                                                                                                                            | Response contains `"status":"UP"`.                                                                    |
| LOCAL-007 | Service liveness                     | `curl -fsS http://localhost:8080/actuator/health/liveness`                                                                                                                                                                                                                                                   | Response contains `"status":"UP"`.                                                                    |
| LOCAL-008 | Service readiness                    | `curl -fsS http://localhost:8080/actuator/health/readiness`                                                                                                                                                                                                                                                  | Response contains `"status":"UP"`.                                                                    |
| LOCAL-009 | Service info endpoint                | `curl -fsS http://localhost:8080/actuator/info`                                                                                                                                                                                                                                                              | Command exits successfully.                                                                           |
| LOCAL-010 | PKB item lookup                      | After seeding a `pkb_item`, export `TOKEN` to a BioCompass access token that is active at the configured auth introspection endpoint, then run `curl -fsS -H "Authorization: Bearer ${TOKEN}" 'http://localhost:8080/api/pkb/items/<item-id>?userId=<user-id>'`                                              | Response contains the requested `itemId`, `userId`, envelope fields, payload, scopes, and timestamps. |
| LOCAL-011 | PKB item search                      | After seeding `pkb_item` rows, export `TOKEN` to a BioCompass access token that is active at the configured auth introspection endpoint, then run `curl -fsS -H "Authorization: Bearer ${TOKEN}" 'http://localhost:8080/api/pkb/items?userId=<user-id>&entityType=observation&status=active&text=hydration'` | Response contains only matching items for the authenticated user scope.                               |
| LOCAL-012 | PKB unauthenticated query denial     | `curl -fsS -o /dev/null -w '%{http_code}' 'http://localhost:8080/api/pkb/items?userId=<user-id>'`                                                                                                                                                                                                            | Response status is `401`.                                                                             |
| LOCAL-013 | PKB cross-user query denial          | Export `TOKEN` to a non-staff BioCompass access token for one user, then run `curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" 'http://localhost:8080/api/pkb/items?userId=<different-user-id>'`                                                                                   | Response code is `403`.                                                                               |
| LOCAL-014 | Artifact metadata registration       | After auth/API exposure and local MinIO startup, register artifact metadata for a document object key such as `users/{user_id}/documents/{document_id}/original`.                                                                                                                                            | PostgreSQL contains the `pkb_artifact`, `pkb_artifact_provenance`, and optional artifact consent rows; object bytes are not stored in PostgreSQL. |

## Feature Testcase Backlog

Add concrete local service testcases here as features land:

- Item create/update/delete HTTP flows.
- Artifact object upload/download HTTP flows after metadata registration.
- Kafka event publication and consumer/idempotency behavior.
- FHIR mapping and resource retrieval behavior.
