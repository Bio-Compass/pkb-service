# Local Verification Testcases

Use this file as the living checklist for local verification. Update it when a code change adds, removes, or changes service behavior.

## Policy

- After every code change, run the relevant automated tests.
- When a code change affects startup, configuration, persistence, infrastructure integration, or HTTP behavior, run the service locally and execute the applicable local service testcases below.
- Add focused unit tests for domain logic, mappers, validators, policy input construction, event payload construction, and service orchestration as those features are implemented.

## Automated Testcases

| ID | Scope | Command | Expected result |
| --- | --- | --- | --- |
| AUTO-001 | Full Gradle test suite | `./gradlew test --no-daemon` | Build succeeds and all tests pass. |
| AUTO-002 | Qodana static-analysis report | `qodana scan --save-report --results-dir .qodana/results` | Command exits successfully and writes `.qodana/results/qodana.sarif.json` plus `.qodana/results/report/index.html`. |
| AUTO-003 | PKB command module tests | `./gradlew test --tests 'com.biocompass.pkb.command.*' --tests 'com.biocompass.pkb.persistence.PkbCommandServiceIntegrationTest' --no-daemon` | Command DTO validation, metadata normalization, transactional writes, supersession, relationship creation, artifact association, and after-commit domain event behavior pass. |

## Local Service Testcases

Start local dependencies before running these checks:

```sh
docker compose --env-file .env up -d
```

Start the service with the local profile:

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

| ID | Scope | Command | Expected result |
| --- | --- | --- | --- |
| LOCAL-001 | Compose dependency health | `docker compose ps` | PostgreSQL, Kafka, MinIO, and OPA containers are running and healthy. |
| LOCAL-002 | PostgreSQL readiness | `docker compose exec postgres pg_isready -U pkb -d pkb` | PostgreSQL accepts connections. |
| LOCAL-003 | Flyway migration state | `docker compose exec postgres psql -U pkb -d pkb -c "select version, success from flyway_schema_history order by installed_rank;"` | Latest expected migration is present with `success = t`. |
| LOCAL-004 | Kafka broker reachability | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list` | Command exits successfully. |
| LOCAL-005 | MinIO readiness | `curl -fsS http://localhost:9000/minio/health/ready` | Command exits successfully. |
| LOCAL-006 | OPA health | `curl -fsS http://localhost:8181/health` | Command exits successfully. |
| LOCAL-007 | OPA local allow policy | `curl -fsS -X POST -H 'Content-Type: application/json' --data '{"input":{"actor":{"user_id":"user-1","roles":["user"]},"action":"read","purpose":"self","resource":{"owner_user_id":"user-1","privacy_scope":"normal"}}}' http://localhost:8181/v1/data/biocompass/pkb/authz/allow` | Response contains `"result":true`. |
| LOCAL-008 | Service startup and aggregate health | `curl -fsS http://localhost:8080/actuator/health` | Response contains `"status":"UP"`. |
| LOCAL-009 | Service liveness | `curl -fsS http://localhost:8080/actuator/health/liveness` | Response contains `"status":"UP"`. |
| LOCAL-010 | Service readiness | `curl -fsS http://localhost:8080/actuator/health/readiness` | Response contains `"status":"UP"`. |
| LOCAL-011 | Service info endpoint | `curl -fsS http://localhost:8080/actuator/info` | Command exits successfully. |

## Feature Testcase Backlog

Add concrete local service testcases here as features land:

- Item create/read/update/delete HTTP flows.
- Command-layer item creation, supersession, relationship creation, and artifact association are covered by `AUTO-002`; add HTTP-level flows once REST APIs are introduced.
- Artifact registration and MinIO object-reference behavior.
- OPA deny-policy scenarios and service-level authorization decisions.
- Kafka event publication and consumer/idempotency behavior.
- FHIR mapping and resource retrieval behavior.
