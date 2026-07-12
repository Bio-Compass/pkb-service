# Local Verification Testcases

Use this file as the living checklist for local verification. Update it when a code change adds, removes, or changes service behavior.

## Policy

- After every code change, run the relevant automated tests.
- When a code change affects startup, configuration, persistence, infrastructure integration, or HTTP behavior, run the service locally and execute the applicable local service testcases below.
- Add focused unit tests for domain logic, mappers, validators, policy input construction, event payload construction, and service orchestration as those features are implemented.

## Automated Testcases

| ID       | Scope                  | Command                      | Expected result                    |
|----------|------------------------|------------------------------|------------------------------------|
| AUTO-001 | Full Gradle test suite | `./gradlew test --no-daemon` | Build succeeds and all tests pass. |
| AUTO-002 | Qodana static-analysis report | `qodana scan --save-report --results-dir .qodana/results` | Command exits successfully and writes `.qodana/results/qodana.sarif.json` plus `.qodana/results/report/index.html`. |
| AUTO-003 | PKB command module tests | `./gradlew test --tests 'com.biocompass.pkb.command.*' --tests 'com.biocompass.pkb.persistence.PkbCommandServiceIntegrationTest' --no-daemon` | Command DTO validation, metadata normalization, transactional writes, supersession, relationship creation, artifact association, and after-commit domain event behavior pass. |
| AUTO-004 | CI deploy change classifier | `./gradlew testCiScripts --no-daemon` | Classifier allows image-only Helm diffs without approval and requires approval for service/runtime or non-image Helm changes. |
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

| ID        | Scope                                | Command                                                                                                                                                                                                                                                                             | Expected result                                                                                       |
|-----------|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| LOCAL-001 | Compose dependency health            | `docker compose ps`                                                                                                                                                                                                                                                                 | PostgreSQL, Kafka, MinIO, and OPA containers are running and healthy.                                 |
| LOCAL-002 | PostgreSQL readiness                 | `docker compose exec postgres pg_isready -U pkb -d pkb`                                                                                                                                                                                                                             | PostgreSQL accepts connections.                                                                       |
| LOCAL-003 | Flyway migration state               | `docker compose exec postgres psql -U pkb -d pkb -c "select version, success from flyway_schema_history order by installed_rank;"`                                                                                                                                                  | Latest expected migration is present with `success = t`.                                              |
| LOCAL-004 | Kafka broker reachability            | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list`                                                                                                                                                                                    | Command exits successfully.                                                                           |
| LOCAL-005 | MinIO readiness                      | `curl -fsS http://localhost:9000/minio/health/ready`                                                                                                                                                                                                                                | Command exits successfully.                                                                           |
| LOCAL-006 | OPA health                           | `curl -fsS http://localhost:8181/health`                                                                                                                                                                                                                                            | Command exits successfully.                                                                           |
| LOCAL-007 | OPA local allow policy               | `curl -fsS -X POST -H 'Content-Type: application/json' --data '{"input":{"actor":{"user_id":"user-1","roles":["user"]},"action":"read","purpose":"self","resource":{"owner_user_id":"user-1","privacy_scope":"normal"}}}' http://localhost:8181/v1/data/biocompass/pkb/authz/allow` | Response contains `"result":true`.                                                                    |
| LOCAL-008 | Service startup and aggregate health | `curl -fsS http://localhost:8080/actuator/health`                                                                                                                                                                                                                                   | Response contains `"status":"UP"`.                                                                    |
| LOCAL-009 | Service liveness                     | `curl -fsS http://localhost:8080/actuator/health/liveness`                                                                                                                                                                                                                          | Response contains `"status":"UP"`.                                                                    |
| LOCAL-010 | Service readiness                    | `curl -fsS http://localhost:8080/actuator/health/readiness`                                                                                                                                                                                                                         | Response contains `"status":"UP"`.                                                                    |
| LOCAL-011 | Service info endpoint                | `curl -fsS http://localhost:8080/actuator/info`                                                                                                                                                                                                                                     | Command exits successfully.                                                                           |
| LOCAL-012 | PKB item lookup                      | After auth integration and seeding a `pkb_item`, run `curl -fsS -H 'Authorization: Bearer <jwt-for-user-id>' 'http://localhost:8080/api/pkb/items/<item-id>?userId=<user-id>'`                                                                                                      | Response contains the requested `itemId`, `userId`, envelope fields, payload, scopes, and timestamps. |
| LOCAL-013 | PKB item search                      | After auth integration and seeding `pkb_item` rows, run `curl -fsS -H 'Authorization: Bearer <jwt-for-user-id>' 'http://localhost:8080/api/pkb/items?userId=<user-id>&entityType=observation&status=active&text=hydration'`                                                         | Response contains only matching items for the authenticated user scope.                               |
| LOCAL-014 | PKB unauthenticated query denial     | `curl -fsS -o /dev/null -w '%{http_code}' 'http://localhost:8080/api/pkb/items?userId=<user-id>'`                                                                                                                                                                                   | Response status is `401`.                                                                             |
| LOCAL-015 | Artifact metadata registration      | After auth/API exposure and local MinIO startup, register artifact metadata for a document object key such as `users/{user_id}/documents/{document_id}/original`.                                                                                                                      | PostgreSQL contains the `pkb_artifact`, `pkb_artifact_provenance`, and optional artifact consent rows; object bytes are not stored in PostgreSQL. |

## Feature Testcase Backlog

Add concrete local service testcases here as features land:

- Item create/update/delete HTTP flows.
- Artifact object upload/download HTTP flows after metadata registration.
- OPA deny-policy scenarios and service-level authorization decisions.
- Kafka event publication and consumer/idempotency behavior.
- FHIR mapping and resource retrieval behavior.
