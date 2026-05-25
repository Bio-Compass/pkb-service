# BioCompass PKB Implementation Plan

## Summary

Implement the BioCompass Personal Knowledge Base (PKB) as a Java 25, Spring Boot 4.0.x, Gradle-based modular monolith.

The service will use PostgreSQL as the canonical store, Flyway for schema migrations, HAPI FHIR as an interoperability facade, OPA for ABAC policy decisions, Kafka for asynchronous enrichment events, and S3-compatible object storage for binary artifacts.

The first implementation is one deployable service with clear internal module boundaries. The architecture should remain split-ready, but the initial codebase should avoid premature microservice deployment complexity.

## Architecture Defaults

- Runtime: Java 25
- Framework: Spring Boot 4.0.x
- Spring Framework: 7.0.x
- Build: Gradle 9.1 or later
- Persistence: PostgreSQL, Hibernate ORM 7.2.x, Flyway
- Search: PostgreSQL full-text search first, pgvector foundation for semantic search
- FHIR: HAPI FHIR Plain Server facade
- Security: Spring Security OAuth2 resource server for BioCompass-issued JWTs
- Policy: OPA for ABAC and purpose-of-use decisions
- Async: Kafka
- Object storage: AWS S3 / MinIO-compatible API
- Local infrastructure: Docker Compose for PostgreSQL, Kafka, MinIO, and OPA

## Implementation Steps

### 1. Scaffold Gradle Spring Boot PKB Service

Create the service foundation.

Tasks:

- Create a Java 25 / Spring Boot 4.0.x Gradle project.
- Add baseline application configuration.
- Add local, test, and default Spring profiles.
- Add a basic health endpoint.
- Add unit and integration test scaffolding.
- Include dependencies for Spring Web, Validation, Security, JPA, Flyway, PostgreSQL, Kafka, S3, HAPI FHIR, and Testcontainers.

Acceptance criteria:

- The project builds with Gradle.
- The service starts locally.
- A health endpoint is available.
- Test scaffolding is present and runnable.
- Core dependencies are declared for later implementation steps.

Local verification:

- Run the Gradle test task locally.
- Run the service locally and verify the health endpoint.

### 2. Add Local Development Infrastructure

Provide a runnable local environment before adding persistence, policy, Kafka, storage, and FHIR behavior.

Tasks:

- Add Docker Compose for PostgreSQL, Kafka, MinIO, and OPA.
- Add local application profile configuration.
- Add sample environment variables.
- Add a sample OPA policy for development.
- Document local startup and test commands.

Acceptance criteria:

- A developer can start local dependencies with Docker Compose.
- The Spring Boot service can connect to local infrastructure.
- Flyway can run against local PostgreSQL once migrations are added.
- OPA, Kafka, and MinIO are reachable in local development.
- Local setup is documented.

Local verification:

- Start local dependencies with Docker Compose.
- Run the Spring Boot service with the local profile.
- Verify the service health endpoint and dependency connectivity.

### 3. Deploy Baseline Service To VM-Hosted Kubernetes

Make the scaffolded service deployable to Kubernetes running on a virtual machine before adding deeper product behavior.

Tasks:

- Build the service image with Gradle Jib using a branch-and-revision tag.
- Add the Helm chart in the separate `Bio-Compass/bio-compass-helm` repository with Deployment, Service, ConfigMap, Secret references, readiness probe, liveness probe, resource requests, and resource limits.
- Use the service health endpoint for Kubernetes probes.
- Target a single Linux virtual machine running a lightweight Kubernetes distribution such as k3s for the first deployment path.
- Document VM prerequisites, image build, image push or local image loading, namespace creation, deployment, verification, log inspection, and rollback commands.
- Keep runtime configuration externalized through environment variables, ConfigMaps, and Secrets.

Acceptance criteria:

- The baseline service image can be built.
- The service can be deployed to a VM-hosted Kubernetes cluster.
- Kubernetes readiness and liveness probes use the health endpoint.
- Configuration is supplied without baking environment-specific values into the image.
- Deployment and rollback steps are documented.

Local verification:

- Build the container image locally.
- Deploy the baseline service to a local or VM-hosted Kubernetes cluster.
- Verify pod readiness, service routing, logs, and rollback commands.

### 4. Add Canonical PostgreSQL Schema With Flyway

Create the canonical PKB persistence model.

Tasks:

- Add Flyway migrations for:
  - `pkb_item`
  - `pkb_provenance`
  - `pkb_relationship`
  - `pkb_consent_binding`
  - `pkb_artifact`
  - `pkb_fact_embedding`
- Add constraints for primary keys, foreign keys, item ownership, artifact ownership, and relationship integrity.
- Add indexes for user-scoped queries, entity type, subtype, status, validity windows, privacy scope, JSONB payload search, full-text search, and vector lookup.
- Enable PostgreSQL extensions needed for JSONB indexing, full-text search, UUIDs if database-generated UUIDs are used, and pgvector.

Acceptance criteria:

- Migrations apply cleanly to a fresh PostgreSQL database.
- The schema represents the architecture document's canonical model.
- Query-critical indexes exist.
- Migration behavior is covered by integration tests.

Local verification:

- Start local PostgreSQL.
- Run Flyway migrations locally.
- Run schema integration tests locally.

### 5. Implement PKB Command Module

Implement write-side behavior for canonical PKB records.

Tasks:

- Add command DTOs for PKB item creation, provenance, relationships, supersession, and artifact association.
- Add validation for required item envelope fields.
- Normalize item metadata before persistence.
- Persist PKB items and provenance in one transaction.
- Support supersession metadata.
- Support relationship creation between PKB items.
- Publish domain events only after successful writes.

Acceptance criteria:

- PKB items can be created through the command layer.
- Provenance is persisted with item writes.
- Supersession fields can be set and queried later.
- Relationships can be created between valid PKB items.
- Failed writes do not publish events.

Local verification:

- Run command module unit tests locally.
- Run a local API create flow against local PostgreSQL.
- Verify created records and provenance in the local database.

### 6. Implement PKB Query Module

Implement read-side retrieval and search.

Tasks:

- Add item lookup by ID.
- Add list/search APIs for user-scoped PKB records.
- Support filters for user, entity type, subtype, status, time validity, privacy scope, and text query.
- Add PostgreSQL full-text search over appropriate item fields.
- Add a repository boundary that can later support pgvector and Elasticsearch projections.
- Enforce policy decisions before returning protected data.

Acceptance criteria:

- PKB items can be retrieved by ID.
- PKB items can be searched with planned filters.
- Text search works against persisted item data.
- Unauthorized data is not returned.
- Query behavior is covered by integration tests.

Local verification:

- Run query module unit and integration tests locally.
- Seed local PKB items and verify ID lookup, filters, and text search.

### 7. Integrate BioCompass Auth And Policy Enforcement

Integrate BioCompass identity and ABAC authorization.

Tasks:

- Configure Spring Security as an OAuth2 resource server.
- Validate JWTs issued by the BioCompass auth service using configurable issuer and JWKS URI.
- Map JWT claims into an internal actor context.
- Include actor ID, user ID, roles, scopes, tenant or context if present, and purpose of use.
- Add an OPA client.
- Build OPA decision input from actor, action, purpose, resource metadata, privacy scope, consent scope, provenance, and trust context.
- Enforce OPA authorization for REST and FHIR operations.
- Provide local development defaults for policy testing without implementing a standalone auth server.

Acceptance criteria:

- The service validates BioCompass-issued JWTs.
- Claims are mapped into internal authorization context.
- OPA decisions gate protected operations.
- Denied requests do not expose protected PKB data.
- Authorization behavior is covered by tests for allow, deny, and redaction outcomes.

Local verification:

- Run authorization unit tests locally.
- Start local OPA.
- Verify allowed and denied REST calls with local test JWTs or configured dev credentials.

### 8. Implement Artifact Storage Integration

Implement metadata and object-reference handling for binary artifacts.

Tasks:

- Add an S3-compatible storage client for AWS S3 and MinIO.
- Add artifact metadata registration.
- Generate deterministic object keys using the architecture convention.
- Store object references, content type, size, hash, provenance, and consent metadata in PostgreSQL.
- Do not store binary payloads directly in PostgreSQL rows.

Recommended object key shape:

```text
users/{user_id}/documents/{document_id}/original
users/{user_id}/documents/{document_id}/ocr.json
users/{user_id}/documents/{document_id}/thumbnail.webp
```

Acceptance criteria:

- Artifact metadata can be registered.
- Object keys are generated consistently.
- Storage configuration supports both MinIO and AWS S3-compatible endpoints.
- Artifact registration can emit enrichment events.

Local verification:

- Start local MinIO.
- Run artifact storage tests locally.
- Register artifact metadata locally and verify object references.

### 9. Add Kafka Eventing And Enrichment Pipeline Foundation

Add the asynchronous workflow foundation.

Tasks:

- Configure Kafka producer and consumer infrastructure.
- Publish events after committed item and artifact writes.
- Use transactionally safe event publishing, such as an outbox-style pattern or equivalent.
- Add initial event types:
  - `pkb.item.created`
  - `pkb.artifact.created`
  - `pkb.enrichment.requested`
  - `pkb.enrichment.completed`
- Add idempotent consumer structure for future OCR, extraction, embeddings, summarization, and terminology mapping.
- Ensure derived facts create or update PKB records with provenance instead of mutating raw source artifacts.

Acceptance criteria:

- Item and artifact writes can emit Kafka events.
- Consumers are idempotent.
- Event payloads include event ID, event type, occurred-at timestamp, user ID, PKB item or artifact ID, and correlation or workflow ID.
- Event behavior is covered by tests.

Local verification:

- Start local Kafka.
- Run Kafka producer and consumer tests locally.
- Verify local event publication and idempotent consumption.

### 10. Implement HAPI FHIR Facade

Expose FHIR interoperability without making FHIR persistence canonical.

Tasks:

- Add HAPI FHIR Plain Server integration.
- Map canonical PKB records to FHIR resources.
- Add initial mappings for:
  - `Patient`
  - `Observation`
  - `Condition`
  - `Goal`
  - `QuestionnaireResponse`
  - `MedicationStatement`
  - `NutritionIntake`
  - `DocumentReference`
  - `Binary`
  - `Provenance`
  - `Consent`
- Route FHIR reads and writes through canonical command/query modules where supported.
- Enforce BioCompass auth and OPA policy for FHIR operations.

Acceptance criteria:

- FHIR endpoints are available through HAPI FHIR.
- FHIR resources map to and from canonical PKB records.
- PostgreSQL canonical tables remain the source of truth.
- FHIR operations enforce the same policy model as native REST APIs.

Local verification:

- Run FHIR mapping unit tests locally.
- Start the service locally and verify supported FHIR endpoints against canonical PKB data.

### 11. Add Implementation Test Suite

Add focused test coverage for the first production slice.

Tasks:

- Add unit tests for validation, normalization, authorization input, FHIR mapping, object key generation, and Kafka payload creation.
- Add integration tests for PostgreSQL/Flyway, REST flows, OPA decisions, S3/MinIO artifact registration, and Kafka producer/consumer behavior.
- Add acceptance-style tests for:
  - PKB item create/read/search.
  - Denied protected-data access.
  - Artifact metadata registration.
  - Enrichment event publication.
  - FHIR Observation read flow.

Acceptance criteria:

- Unit tests run through Gradle.
- Integration tests run against containerized dependencies where practical.
- Core command, query, policy, artifact, Kafka, and FHIR flows have coverage.
- The test suite is documented for local execution.

Local verification:

- Run the full local test suite with Gradle.
- Run acceptance-style tests against local dependencies.

## Public Interfaces

Native REST APIs are the primary BioCompass product APIs for PKB operations.

Initial REST capabilities:

- Create PKB item.
- Retrieve PKB item by ID.
- Search/list PKB items.
- Create PKB relationships.
- Register artifact metadata.

FHIR APIs are interoperability endpoints, not the canonical data model.

Initial FHIR capabilities:

- Read canonical PKB data through mapped FHIR resources.
- Accept supported FHIR writes only when they can be normalized into canonical PKB commands.
- Apply the same auth and policy checks used by native REST.

Kafka events are internal integration contracts.

Initial event fields:

- Event ID
- Event type
- Occurred-at timestamp
- User ID
- PKB item ID or artifact ID
- Correlation or workflow ID

## Testing Strategy

All implemented code should have unit test coverage. Integration and acceptance tests add confidence across infrastructure boundaries, but they do not replace unit tests for domain logic, mappers, validators, policy input construction, event payload construction, and service orchestration.

Use layered testing:

- Unit tests for isolated domain logic and mappers.
- Repository tests for Flyway and PostgreSQL behavior.
- API tests for REST and FHIR behavior.
- Policy tests for OPA allow, deny, and redaction decisions.
- Storage tests for S3/MinIO object references.
- Kafka tests for event publishing and idempotent consumption.

The minimum acceptance scenario for the first implementation is:

1. A user creates a health-related PKB item with provenance and consent scopes.
2. A permitted actor retrieves the item for an allowed purpose.
3. A denied actor receives no protected data.
4. A document artifact is registered and emits an enrichment event.
5. A FHIR Observation can be read from canonical PKB data.

## Assumptions And Deferred Work

Assumptions:

- This repository owns the PKB service implementation.
- BioCompass auth exists or will exist separately; this service validates and consumes BioCompass-issued JWTs.
- The initial deployment shape is a modular monolith.
- PostgreSQL is the canonical source of truth.
- HAPI FHIR is a facade, not the primary persistence layer.

Deferred work:

- Splitting modules into independent microservices.
- Elasticsearch projection.
- Graph database projection.
- Production OCR, extraction, summarization, embedding, and terminology engines.
- Advanced multilingual search ranking.
- Full production policy authoring workflow.
