# BioCompass PKB Service

Personal Knowledge Base service for BioCompass.

## Running Locally

The local setup uses Docker Compose for infrastructure and the Spring Boot `local` profile for service configuration. See [docs/local-development.md](docs/local-development.md) for detailed dependency checks and troubleshooting.

### Prerequisites

- Java 25
- Docker or Docker Desktop
- Docker Compose v2

### Start Local Dependencies

Create a local environment file:

```sh
cp .env.example .env
```

Start PostgreSQL, Kafka, MinIO, and OPA:

```sh
docker compose --env-file .env up -d
```

Check that the containers are healthy:

```sh
docker compose ps
```

### Run The Service

Run the service with the local Spring profile:

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

By default, the service listens on `http://localhost:8080` and uses these local dependency endpoints:

| Dependency | Endpoint |
| --- | --- |
| PostgreSQL | `localhost:5432` |
| Kafka | `localhost:9092` |
| MinIO S3 API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| OPA | `http://localhost:8181` |

Verify the service health endpoint:

```sh
curl -fsS http://localhost:8080/actuator/health
```

The local profile enables the Kafka outbox relay. The enrichment consumer is activated only when an
enrichment workflow implementation is installed. After startup, confirm the PKB topics were created with:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
```

If you change values in `.env`, export them before running Spring Boot because `docker compose --env-file .env` only applies them to Compose:

```sh
set -a
source .env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Run Tests

```sh
./gradlew test --no-daemon
```

The tests use Testcontainers for infrastructure dependencies, so the Compose stack does not need to be running.
Use [docs/local-verification-testcases.md](docs/local-verification-testcases.md) as the service verification checklist after code changes.

To run only the Kafka eventing tests:

```sh
./gradlew test \
  --tests 'com.biocompass.pkb.command.event.*' \
  --tests 'com.biocompass.pkb.eventing.*' \
  --no-daemon
```

After a feature that affects startup, persistence, security, or HTTP behavior, keep the local service running and execute the black-box E2E suite:

```sh
./gradlew e2eTest --no-daemon
```

The E2E suite seeds PostgreSQL, starts a temporary BioCompass auth introspection stub on the local-profile default port, and drives the service through HTTP with opaque Bearer tokens.

### Qodana Report

The Qodana GitHub Actions workflow uploads a `qodana-report` artifact for each run. The artifact includes the SARIF output, Qodana logs, and the saved HTML report under `report/index.html`.

To reproduce the report locally, install the Qodana CLI and run:

```sh
qodana scan --save-report --results-dir .qodana/results
```

### Stop Local Dependencies

```sh
docker compose down
```

To stop containers and delete local volumes:

```sh
docker compose down -v
```

## Client-neutral source records (PKB #62)

The [generic record API](docs/pkb_record_api.md) supports BWTS storage while keeping PKB
independent of mobile, browser, Telegram and other clients. It uses separate workload
authentication, BAS backend authority verification, and AU enforcement. The existing
asynchronous command ingress remains unchanged. Enable only after the companion BAS/BWTS
changes and policy/edge configuration have been reviewed; this branch makes no deployment claim.

Local BWTS-to-PKB verification: build `bootJar`, then run
`manual_tests/verify_bwts_pkb.py` with the BWTS external Python environment, `--bwts-checkout`,
`--pkb-jar`, `--evidence-dir` and disposable `PKB_TEST_DATABASE_URL`,
`PKB_TEST_DATABASE_USER`, `PKB_TEST_DATABASE_PASSWORD`. The harness uses real PKB/PostgreSQL
and explicit BAS/BUPS/AU contract doubles.

Legacy `/api/pkb/**` fact APIs require an explicitly allowed service credential **and** BAS user
bearer authority. A user or staff token alone cannot call PKB. See [Service-only access](docs/pkb-service-access.md)
for configuration, compatibility and the distinct source-record authentication contract.
