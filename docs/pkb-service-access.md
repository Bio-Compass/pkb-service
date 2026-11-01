# Service-only PKB fact access

All PKB fact APIs authenticate the calling workload independently of the user whose facts are
accessed. Mobile, browser, Telegram and other clients call the owning application service, such
as BWTS. A user or staff token alone never authenticates a PKB workload.

This is an application-level check. Private-network routing and public-edge isolation are still
required deployment controls. Health/info and API documentation retain their existing operational
access rules; they expose no fact operations and are not a user-facing PKB application.

## Legacy commands and queries

Every `/api/pkb/items` or `/api/pkb/commands/**` request requires all three headers:

| Header | Meaning |
| --- | --- |
| `X-BioCompass-Service` | Explicitly approved incoming service identity |
| `X-BioCompass-Service-Token` | Directed credential for that service's legacy PKB capability |
| `Authorization: Bearer <user token>` | Original BAS user authority, introspected by PKB |

The workload check runs before bearer introspection, body validation, AU, data access or Kafka.
Missing, unknown, wrong or repeated workload headers receive bounded `401 unauthorized_service`
without echoing inputs. Supplying only a workload credential still fails user authentication.
User scope, staff/owner read policy and write AU checks remain enforced after both identities are
authenticated. This change does not grant new BAS audiences, token classes or user permissions.

Configure `biocompass.pkb.auth.legacy.service-tokens` in external Spring configuration. Its default
is an **empty map**, so no legacy caller is implicitly trusted. A structural example is:

```json
{
  "biocompass": {
    "pkb": {
      "auth": {
        "legacy": {
          "service-tokens": {
            "approved-service": "<separately provisioned directed credential>"
          }
        }
      }
    }
  }
}
```

Spring supports this structure through `SPRING_APPLICATION_JSON`, an external YAML file or an
existing secret/configuration provider. Provision real values separately; do not commit or print
them. Blank, padded or duplicate credentials are invalid. Legacy credentials must be distinct
from source-record credentials and PKB's outbound BAS/AU credentials. A credential from one
capability cannot authenticate in the other by changing a service-name header.

Granting this capability allows the service to invoke the legacy fact API under independently
validated user authority; it is a privileged, explicit operator choice. BWTS is **not** granted it
by its source-record credential. No production caller or default service secret is added here.

The current BAS companion authorizes PKB's narrow backend verifier for BWTS; it does not add PKB
to ordinary introspection callers. Keep production legacy callers unconfigured until BAS ownership
approves the matching user-authority contract. Do not borrow another service's BAS credential.
Legacy positive-path tests use an explicit introspection contract fixture; the real BAS/BWTS path
uses the approved backend verifier. This workload gate neither widens BAS policy nor claims that
a production legacy integration is enabled.

## Source-record API

`/api/v1/internal/items/**` retains [its separate contract](pkb_record_api.md): producer credential
in `Authorization`, the approved producer in `X-BioCompass-Service`, and original user authority
in `X-BioCompass-User-Token`. BAS verifies the approved backend/resource relationship and AU
decides each operation. It does not accept the legacy capability as a substitute.

OpenAPI describes the joint workload/user requirements for each plane as one AND requirement;
they are not alternative authentication methods.

## Commands and compatibility

HTTP commands remain asynchronous and return `202` only after AU allows and Kafka acknowledges.
The incoming service authenticated by PKB supplies the safe `producerService` context for both
HTTP-time and consumer-time AU and the inbox audit row. `PKB_KAFKA_PRODUCER_SERVICE` cannot override
this identity. The actual broker publisher is still the PKB workload, subject to broker ACLs.
Neither user tokens nor service credentials enter the Kafka envelope or canonical records.

Existing clients using only a user bearer must add a separately approved directed credential.
There is no user-only compatibility switch. Previously accepted messages retain their original
producer audit context and still undergo consumer-time authorization; this does not rewrite history.

## Local verification

The full E2E service must explicitly configure `pkb-e2e-client` with test-only credential
`pkb-e2e-directed-secret` under the legacy map. Those values are test fixture defaults, never
production defaults. `PKB_E2E_SERVICE_NAME` and `PKB_E2E_SERVICE_TOKEN` override the E2E client;
configure the running service with the matching pair. The E2E suite verifies direct user/staff
requests fail before introspection, approved service/user queries still work, and a committed
Kafka command records the authenticated caller rather than an arbitrary header/configuration.

The local three-service BWTS launcher needs no legacy credential: BWTS uses only the source-record
API. Its successful flow must continue while direct user calls to either PKB fact plane fail.

The shared deployed Java E2E runner also requires `PKB_E2E_SERVICE_NAME` and
`PKB_E2E_SERVICE_TOKEN` explicitly, without the local fixture defaults. GitHub's `testing`
environment supplies them as secrets; orchestration sends them through stdin to the temporary
Job-owned Secret. Its PKB requests include both workload headers, while BAS login receives only
account credentials. Missing workload settings fail before creating cluster resources or logging
in. Provisioning a matching PKB capability and approving BAS authority remain separate operator
steps; no namespace secrets or deployed settings are changed by this source update.
