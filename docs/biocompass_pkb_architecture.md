# BioCompass PKB Architecture Proposal

## Overview

This document describes the recommended architecture for the BioCompass Personal Knowledge Base (PKB).

The PKB is:

- time-aware
- trust-aware
- provenance-aware
- consent-aware
- multimodal
- policy-governed

The PKB is NOT:

- generic profile storage
- settings storage
- audit log
- chat history
- random JSON dump

---

# Recommended Architecture

## Final Recommendation

```text
PostgreSQL canonical PKB
+ HAPI FHIR interoperability facade
+ ABAC policy engine (OPA)
+ S3-compatible object storage
+ pgvector + PostgreSQL full-text search
+ optional Elasticsearch projection later
```

---

# High-Level Architecture

```text
                ┌──────────────────────┐
                │  PKB API Gateway     │
                └──────────┬───────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌────────────────┐  ┌────────────────┐
│ PKB Command  │  │ PKB Query      │  │ Policy Service │
│ Service      │  │ Service        │  │ (OPA)          │
└──────┬───────┘  └────────┬───────┘  └────────────────┘
       │                   │
       ▼                   ▼
┌────────────────────────────────────────────┐
│ PostgreSQL (canonical PKB)                │
│--------------------------------------------│
│ pkb_item                                   │
│ pkb_provenance                             │
│ pkb_relationship                           │
│ pkb_consent_binding                        │
│ pkb_artifact                               │
│ pkb_fact_embedding                         │
└────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ S3-compatible object storage │
│ (MinIO / AWS S3)             │
└──────────────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Async enrichment pipelines   │
│------------------------------│
│ OCR                          │
│ extraction                   │
│ embeddings                   │
│ summarization                │
│ terminology mapping          │
│ derived facts                │
└──────────────────────────────┘
```

The implemented PostgreSQL tables and field responsibilities are documented in
[PKB Canonical PostgreSQL Schema](pkb_canonical_schema.md).

---

# Why PostgreSQL

PostgreSQL is the best canonical PKB store because PKB needs:

- transactions
- consistency
- auditability
- structured + semi-structured data
- row-level security
- JSON support
- full-text search
- vector search
- partitioning
- reliable backups

PostgreSQL provides:

- JSONB
- GIN indexes
- MVCC
- Row-Level Security
- partitioning
- pgvector
- full-text search

This is much safer than using MongoDB as the primary source of truth.

---

# Why NOT MongoDB as Primary Store

MongoDB is flexible but PKB's hardest problems are:

- consent
- provenance
- trust semantics
- supersession
- time validity
- policy enforcement

Not document flexibility.

MongoDB is acceptable as a projection layer, not ideal as canonical health-sensitive truth storage.

---

# Why NOT Graph DB First

Graph DBs are useful later for:

- recommendation reasoning
- evidence graphs
- relationship traversal

But graph DBs are poor primary stores for:

- transactional correctness
- auditability
- health governance
- policy-heavy workflows

Neo4j can be added later as a projection.

---

# Why FHIR Matters

FHIR should be:

- interoperability layer
- validation layer
- external API language

FHIR should NOT completely define internal storage.

Recommended approach:

```text
Internal canonical model
        ↓
FHIR projection layer
        ↓
External interoperability
```

---

# HAPI FHIR Usage

Use:

- HAPI FHIR Plain Server
- not HAPI JPA as primary persistence

Why:

- you control schema
- avoid FHIR persistence rigidity
- easier PKB evolution
- better product-specific modeling

---

# Recommended PKB Item Envelope

```sql
CREATE TABLE pkb_item (
    pkb_item_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,

    entity_type TEXT NOT NULL,
    subtype TEXT NOT NULL,

    status TEXT NOT NULL,

    payload JSONB NOT NULL,

    source_type TEXT NOT NULL,
    source_id TEXT,

    observed_at TIMESTAMPTZ,
    ingested_at TIMESTAMPTZ NOT NULL,

    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,

    language TEXT,

    consent_scope TEXT[],
    privacy_scope TEXT[],

    verification_status TEXT,

    supersedes UUID,
    superseded_by UUID,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

---

# Recommended Additional Tables

## Provenance

```sql
CREATE TABLE pkb_provenance (
    provenance_id UUID PRIMARY KEY,
    pkb_item_id UUID NOT NULL,

    source_kind TEXT NOT NULL,
    actor_type TEXT,
    workflow_id TEXT,

    source_reference TEXT,
    extraction_method TEXT,

    created_at TIMESTAMPTZ NOT NULL
);
```

---

## Relationships

```sql
CREATE TABLE pkb_relationship (
    relationship_id UUID PRIMARY KEY,

    from_item_id UUID NOT NULL,
    to_item_id UUID NOT NULL,

    relationship_type TEXT NOT NULL
);
```

---

## Artifacts

```sql
CREATE TABLE pkb_artifact (
    artifact_id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    object_key TEXT NOT NULL,
    content_type TEXT,
    sha256 TEXT,

    size_bytes BIGINT,

    created_at TIMESTAMPTZ NOT NULL
);
```

---

# S3-Compatible Object Storage

Recommended:

- AWS S3 for production
- MinIO for dev/stage/self-hosted

Never store binaries directly in PostgreSQL rows.

Store:

```text
PostgreSQL:
- metadata
- hashes
- provenance
- consent
- references

Object storage:
- PDFs
- images
- scans
- raw uploads
```

---

# Recommended Object Keys

```text
users/{user_id}/documents/{document_id}/original.pdf
users/{user_id}/documents/{document_id}/ocr.json
users/{user_id}/documents/{document_id}/thumbnail.webp
```

---

# Kubernetes Storage Recommendation

## Development / Staging

Use:

```text
MinIO Operator
```

inside Kubernetes.

## Production

Prefer:

```text
Managed S3-compatible storage
```

because:

- durability
- backup
- lifecycle management
- disaster recovery
- encryption
- compliance

are difficult to operate correctly yourself.

---

# Trust Model

Avoid fake single trust score.

Use multiple dimensions:

```text
source_reliability
verification_status
freshness_score
self_report_uncertainty
derivation_confidence
overall_trust_level
```

---

# Time Semantics

Every PKB item should support:

```text
observed_at
ingested_at
valid_from
valid_until
decay_rule
supersession
```

---

# Consent & Privacy

Use ABAC instead of only RBAC.

Recommended stack:

```text
OIDC/OAuth2
+ Spring Security
+ OPA
+ PostgreSQL RLS
```

---

# OPA Policy Example

```rego
allow {
    input.actor.role == "doctor"
    input.resource.privacy_scope[_] == "medical"
    input.purpose == "treatment"
}
```

---

# Search Strategy

Start with:

```text
PostgreSQL full-text search
+ pgvector
```

Avoid Elasticsearch initially.

Add Elasticsearch later ONLY if:

- search scale grows
- ranking becomes complex
- multilingual analyzers needed
- near-real-time indexing needed

---

# Embedding Storage

```sql
CREATE TABLE pkb_fact_embedding (
    pkb_item_id UUID PRIMARY KEY,
    embedding vector(1536)
);
```

---

# Recommended Java Stack

## Core

```text
Java 25
Spring Boot 3
PostgreSQL
Hibernate 6
Flyway
```

---

## FHIR

```text
HAPI FHIR
```

---

## Security

```text
Spring Security
OPA
OAuth2/OIDC
```

---

## Async Processing

```text
Kafka
or
RabbitMQ
```

---

## Storage

```text
AWS S3 / MinIO
```

---

# Recommended Microservices

## PKB Command Service

Responsible for:

- writes
- validation
- normalization
- provenance
- trust assignment

---

## PKB Query Service

Responsible for:

- retrieval
- filtering
- semantic search
- ranking

---

## Policy Service

Responsible for:

- ABAC
- consent enforcement
- redaction
- purpose-of-use checks

---

## Artifact Pipeline

Responsible for:

- OCR
- extraction
- summarization
- embeddings
- terminology mapping

---

## FHIR Facade

Responsible for:

- interoperability
- validation
- SMART/FHIR integrations

---

# Recommended FHIR Resource Mapping

| PKB Concept | FHIR Resource |
|---|---|
| demographics | Patient |
| goals | Goal |
| symptoms | Observation |
| conditions | Condition |
| questionnaire answers | QuestionnaireResponse |
| medications | MedicationStatement |
| food intake | NutritionIntake |
| documents | DocumentReference |
| binaries | Binary |
| provenance | Provenance |
| consent | Consent |
| audit | AuditEvent |

---

# Final Recommendation

For BioCompass PKB:

## Use

```text
PostgreSQL
+ JSONB
+ pgvector
+ HAPI FHIR
+ OPA
+ S3-compatible storage
```

## Avoid

```text
FHIR-only persistence
MongoDB-first
Graph-first
Random JSON storage
```

---

# Final Architecture Decision

The best architecture is:

```text
Relational-first
FHIR-aligned
policy-centric
append-mostly
multimodal
projection-based
```

This gives:

- correctness
- explainability
- interoperability
- governance
- scalability
- flexibility
