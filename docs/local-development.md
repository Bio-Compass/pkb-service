# Local Development

This setup starts the infrastructure needed by the PKB service in local development:

- PostgreSQL with pgvector
- Apache Kafka in single-node KRaft mode
- MinIO for S3-compatible object storage
- OPA with a development policy bundle

## Prerequisites

- Docker or Docker Desktop
- Docker Compose v2
- Java 25 and Gradle once the service scaffold is present

## Start Infrastructure

Create a local environment file:

```sh
cp .env.example .env
```

Start all local dependencies:

```sh
docker compose --env-file .env up -d
```

Check service status:

```sh
docker compose ps
```

## Local Endpoints

| Dependency | Endpoint | Default credentials |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | `pkb` / `pkb-local-password` |
| Kafka | `localhost:9092` | none |
| MinIO S3 API | `http://localhost:9000` | `pkb-local-access` / `pkb-local-secret` |
| MinIO Console | `http://localhost:9001` | `pkb-local-access` / `pkb-local-secret` |
| OPA | `http://localhost:8181` | none |

## Verify Dependencies

Verify PostgreSQL:

```sh
docker compose exec postgres pg_isready -U pkb -d pkb
```

Verify Kafka:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
```

Verify MinIO:

```sh
curl -fsS http://localhost:9000/minio/health/ready
```

Verify OPA:

```sh
curl -fsS http://localhost:8181/health
```

Evaluate the local development policy:

```sh
curl -fsS \
  -X POST \
  -H 'Content-Type: application/json' \
  --data '{"input":{"actor":{"user_id":"user-1","roles":["user"]},"action":"read","purpose":"self","resource":{"owner_user_id":"user-1","privacy_scope":"normal"}}}' \
  http://localhost:8181/v1/data/biocompass/pkb/authz/allow
```

## Run The Service Locally

After the Spring Boot scaffold is present, run the service with the local profile:

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

The packaged Spring configuration in `src/main/resources/application-local.yml` points the service at the local Compose dependencies.

If you changed values in `.env`, export them before running Spring Boot because `docker compose --env-file .env` only applies them to Compose:

```sh
set -a
source .env
set +a
./gradlew bootRun --args='--spring.profiles.active=local'
```

Verify the service health endpoint:

```sh
curl -fsS http://localhost:8080/actuator/health
```

## Run Tests

Run the full test suite:

```sh
./gradlew test --no-daemon
```

The local infrastructure tests use Testcontainers to start PostgreSQL, Kafka, MinIO, and OPA automatically. They do not require the Compose stack to be running.

## Stop Infrastructure

Stop containers without deleting data:

```sh
docker compose down
```

Stop containers and delete local volumes:

```sh
docker compose down -v
```
