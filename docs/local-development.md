# Local Development

This setup starts the infrastructure needed by the PKB service in local development:

- PostgreSQL with pgvector
- Apache Kafka in single-node KRaft mode
- MinIO for S3-compatible object storage
- Open Policy Agent (OPA) with a development policy

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

| Dependency    | Endpoint                | Default credentials                     |
|---------------|-------------------------|-----------------------------------------|
| PostgreSQL    | `localhost:5432`        | `pkb` / `pkb-local-password`            |
| Kafka         | `localhost:9092`        | none                                    |
| MinIO S3 API  | `http://localhost:9000` | `pkb-local-access` / `pkb-local-secret` |
| MinIO Console | `http://localhost:9001` | `pkb-local-access` / `pkb-local-secret` |
| OPA           | `http://localhost:8181` | none                                    |

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

Evaluate the development write decision contract:

```sh
curl -fsS \
  -X POST \
  -H 'Content-Type: application/json' \
  --data '{"input":{"command_id":"11111111-1111-1111-1111-111111111111","producer_service":"pkb-service","actor":{"actor_id":"22222222-2222-2222-2222-222222222222","user_id":"22222222-2222-2222-2222-222222222222","roles":["user"],"scopes":[],"purpose_of_use":"self"},"action":"write","resource":{"target_user_id":"22222222-2222-2222-2222-222222222222","command_type":"CreatePkbItemCommand","source_type":"manual","consent_scope":[],"privacy_scope":[],"identifiers":{}}}}' \
  http://localhost:8181/v1/data/biocompass/pkb/authz/decision
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

All non-actuator endpoints require a Bearer token that can be introspected by
the BioCompass auth service. The local profile sends tokens to
`PKB_AUTH_INTROSPECTION_URL` with `Authorization: Bearer
${PKB_AUTH_INTROSPECTION_SERVICE_TOKEN}` and `X-BioCompass-Service:
${PKB_AUTH_INTROSPECTION_SERVICE_NAME}`. For local policy checks, run the auth
service locally or point these variables at a reachable BioCompass auth service,
then obtain an access token from the auth service and export it:

```sh
export TOKEN='<BioCompass access token>'
export USER_ID='<BioCompass user UUID>'
```

Verify a local PKB query request after seeding `pkb_item` rows for the user:

```sh
curl -fsS \
  -H "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/pkb/items?userId=${USER_ID}"
```

## Submit A PKB Write Command

PKB write endpoints acknowledge a command after Kafka accepts it, then the local
consumer applies the canonical PostgreSQL write. Supply a new UUID in
`X-Command-Id`; reuse it only when retrying the same request.

```sh
COMMAND_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"

curl -fsS -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Command-Id: ${COMMAND_ID}" \
  -H "Content-Type: application/json" \
  "http://localhost:8080/api/pkb/commands/items?userId=${USER_ID}" \
  --data '{
    "entityType":"observation",
    "subtype":"note",
    "status":"active",
    "payload":{"text":"Drink water"},
    "sourceType":"manual",
    "provenance":{"sourceKind":"manual"}
  }'
```

The response is `202 Accepted`. The request is not a completed PKB write at
that point; wait for the consumer before reading the new item. The response is
sent only after the HTTP-time AU decision succeeds and Kafka acknowledges the
record. The consumer obtains a fresh AU decision before its PostgreSQL
transaction commits.

Reusing the same command ID is safe only for the exact same user, command type,
and canonical payload. An exact redelivery increments the inbox delivery count
without invoking the command handler again. A mismatched reuse is retained on
the dead-letter topic.

The local profile uses these command topics:

- `pkb.commands.ingress.v1`
- `pkb.commands.ingress.dlt.v1`
- `pkb.commands.ingress.replay.v1`

See [Kafka Command Ingress](kafka-command-ingress.md) for authorization context,
retry classification, manual replay, topic provisioning, and ACL ownership.

## Run Tests

Run the full test suite:

```sh
./gradlew test --no-daemon
```

The local infrastructure tests use Testcontainers to start PostgreSQL, Kafka,
MinIO, and OPA automatically. The Kafka failure tests boot the production
listener and error handler against real Kafka and PostgreSQL and cover transient
AU/database recovery, exhaustion, malformed JSON, DLT retention, idempotency,
and replay. They do not require the Compose stack to be running.

Use [Local Verification Testcases](local-verification-testcases.md) as the checklist for local service verification after code changes.

When a feature affects startup, persistence, security, or HTTP behavior, keep the local service running and execute the black-box E2E suite:

```sh
./gradlew e2eTest --no-daemon
```

The E2E suite seeds PostgreSQL, starts a temporary BioCompass auth introspection stub on the local-profile default port, and drives the service through HTTP with opaque Bearer tokens.

## Stop Infrastructure

Stop containers without deleting data:

```sh
docker compose down
```

Stop containers and delete local volumes:

```sh
docker compose down -v
```
