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

### Stop Local Dependencies

```sh
docker compose down
```

To stop containers and delete local volumes:

```sh
docker compose down -v
```
