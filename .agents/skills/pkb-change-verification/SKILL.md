---
name: pkb-change-verification
description: Select and run focused PKB service verification for Java, Spring, persistence, Kafka ingress, artifact storage, HTTP API, or full end-to-end changes.
---

# PKB change verification

Use `scripts/verify.sh <mode>` from this skill directory. Select the smallest mode that covers the changed behavior:

- `command`: command domain and transactional command handling
- `api`: command HTTP controllers, authorization, validation, and mapping
- `kafka`: Kafka serialization, publication, consumption, and inbox behavior
- `persistence`: PostgreSQL repositories and migrations
- `artifact`: artifact storage and related command persistence
- `full`: all automated tests plus end-to-end tests against the required local services

Run `full` for cross-cutting changes or when startup or HTTP behavior must be verified end to end. If its local infrastructure prerequisites are unavailable, run the applicable focused modes and report the omitted verification.

Do not scan unrelated repository areas or duplicate commands already defined in Gradle. Do not deploy; deployment requires separate manual approval.
