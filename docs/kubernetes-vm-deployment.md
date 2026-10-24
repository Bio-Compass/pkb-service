# VM-Hosted Kubernetes Deployment

This deployment path targets a single Linux virtual machine running k3s. The PKB service repository owns the Java service image. The Helm chart is published as an OCI chart and deployed from the chart registry.

## Prerequisites

- A Linux VM with Docker or containerd access.
- k3s installed and reachable with `kubectl`.
- Helm 3 installed on the machine running deployment commands.
- Java 25 available on the build machine.
- Docker available on the build machine when building into a local Docker daemon.
- Access to the published PKB service Helm chart.

Verify cluster access:

```sh
kubectl get nodes
helm version --short
```

Create the namespace before using the namespace-scoped deployer service account:

```sh
kubectl create namespace bio-compass
```

Set the Helm chart reference:

```sh
HELM_CHART=oci://ghcr.io/bio-compass/charts/pkb-service
```

## Build The Service Image

The service image is built with Gradle Jib. The image tag is generated from the current branch name and short git revision:

```sh
IMAGE_TAG=$(./gradlew -q printContainerImageTag)
echo "${IMAGE_TAG}"
```

Run tests and build the image into the local Docker daemon:

```sh
./gradlew test jibDockerBuild --no-daemon
docker image inspect "pkb-service:${IMAGE_TAG}"
```

For a k3s VM, either build on the VM or save and import the image:

```sh
docker save "pkb-service:${IMAGE_TAG}" | gzip > "pkb-service-${IMAGE_TAG}.tar.gz"
scp "pkb-service-${IMAGE_TAG}.tar.gz" <vm-user>@<vm-host>:/tmp/
ssh <vm-user>@<vm-host> "sudo k3s ctr images import /tmp/pkb-service-${IMAGE_TAG}.tar.gz"
```

If you publish to a registry instead, build and push the same branch-and-revision tag:

```sh
./gradlew test jib --no-daemon -PcontainerImage=<registry>/pkb-service
```

Set the image repository and tag during Helm install or upgrade:

```sh
helm upgrade --install pkb-service "${HELM_CHART}" \
  --namespace bio-compass \
  --set image.repository=pkb-service \
  --set image.pullPolicy=IfNotPresent \
  --set image.tag="${IMAGE_TAG}"
```

For registry images, build and push with `-PcontainerImage=<registry>/pkb-service`, then set `image.repository=<registry>/pkb-service`. The BioCompass Helm defaults expect `ghcr.io/bio-compass/pkb-service`.

## GitHub Actions: testing before production

Tracked by [PKB #66](https://github.com/Bio-Compass/pkb-service/issues/66). The CI workflow builds the application once after Gradle and local-service E2E pass. It records the pushed image digest and the resolved Helm repository commit. Both environment deployments use that same `tag@sha256:digest` and Helm commit; production never rebuilds the image or re-resolves mutable Helm `main`.

The job sequence is:

1. `test` and local `e2e-test` validate the source.
2. `publish-image` publishes and records the immutable image and Helm revision.
3. `deploy-testing` plans the `testing` namespace diff, requests approval for non-image changes, then invokes the existing Helm-owned `scripts/deploy.sh testing pkb-service <tag@digest>` with `DEPLOY_SOURCE=local` at the pinned commit. It waits for rollout, verifies the image and checks readiness.
4. `e2e-testing` runs `.github/scripts/deployed-e2e.py` against the deployed testing PKB and BAS through namespace-scoped Kubernetes port forwards. It requires a real, verified, non-staff synthetic BAS user. Missing credentials, failed readiness, failed login, and API failures fail the job.
5. `deploy-prod` is eligible only after testing E2E succeeds. It plans the production diff, always requires the `prod-manual-approval` environment, and deploys the same immutable image through Helm-owned machinery using `values/prod/pkb-service.yaml`.

Failed, cancelled or skipped testing jobs cannot promote production. Main-branch runs are serialized without cancelling a deployment already in progress. Helm source and values are read from `Bio-Compass/bio-compass-helm` (overridable with `PKB_SERVICE_HELM_VALUES_REPOSITORY` and `PKB_SERVICE_HELM_VALUES_REF`). This service does not change Helm-owned scripts or charts.

### GitHub environment setup

Create GitHub environments `testing`, `prod`, `testing-manual-approval`, and `prod-manual-approval`. Configure required reviewers on both approval environments. The workflow checks that reviewers exist and fails closed if the approval environment is unprotected. The approval URL links to the workflow run with a namespace-specific `deploy-review-testing` or `deploy-review-prod` Helm diff artifact. Review that diff before approving.

Each runtime environment needs its own `KUBE_CONFIG` secret. Credentials must access only the matching namespace. The legacy repository credential scoped to `bio-compass` cannot deploy to either new namespace. Configure `BIO_COMPASS_HELM_VALUES_SSH_KEY` as the existing repository-scoped read-only Helm deploy key. Application/database/S3 secrets remain in the namespace's existing `pkb-service-secrets`; the pipeline never re-renders them from legacy `dev` secrets. Helm environment values must use `secrets.create=false` and reference the existing Secret. Rendered Secret resources cause CI to fail.

The `testing` GitHub environment also needs `PKB_E2E_USERNAME` (BAS email) and `PKB_E2E_PASSWORD`. The synthetic account must be active, email-verified and non-staff. Create/verify it through BAS's supported operator workflow; store its password directly in GitHub Secrets. Never use a production account or weaken BAS introspection for tests. BAS login is performed anew on every run, so no expiring user access token is saved in CI.

The deployer manifests in `deploy/kubernetes/sa-deployer` can be rendered for each namespace for administrator review:

```sh
for namespace in testing prod; do
  { cat deploy/kubernetes/sa-deployer/sa.yaml; echo '---'; cat deploy/kubernetes/sa-deployer/rbac.yaml; } \
    | sed "s/namespace: bio-compass/namespace: ${namespace}/g" > "/tmp/pkb-deployer-${namespace}.yaml"
  kubectl --context biocompass-netcup diff -f "/tmp/pkb-deployer-${namespace}.yaml"
done
```

Applying RBAC, creating namespace-scoped tokens and storing each kubeconfig in the matching GitHub environment are administrator operations requiring separate review. The role includes `pods/portforward:create` for API E2E/readiness checks. Track and rotate bounded CI token expirations; do not upload an administrator kubeconfig. This source change alone does not provision either environment's deployment credentials or activate a rollout.

### Deployed E2E coverage and local replay

The deployed suite covers readiness/liveness, OpenAPI availability, real BAS login, authenticated user-scoped PKB search, rejection of unauthenticated/invalid tokens, cross-user denial, query validation and missing-item behavior. It is read-only against PKB; it does not seed SQL, install an introspection stub, or mutate production data. The existing Gradle `e2eTest` remains the broader isolated local suite, including Kafka write/deduplication tests. Deployed write-path coverage requires the environment's command-ingress/AU configuration and a separate fixture lifecycle; passing read E2E does not claim write-path verification.

For local replay, start port forwards in separate terminals:

```sh
kubectl --context biocompass-netcup -n testing port-forward service/pkb-service 18080:80
kubectl --context biocompass-netcup -n testing port-forward service/bio-compass-auth 18081:80
```

Then run with the account credentials supplied privately in the environment:

```sh
PKB_E2E_NAMESPACE=testing PKB_E2E_BASE_URL=http://127.0.0.1:18080 \
  PKB_E2E_AUTH_URL=http://127.0.0.1:18081 python3 .github/scripts/deployed-e2e.py
```

The script accepts only loopback URLs and `testing` as the namespace. It supplies BAS's internal Host and ingress scheme metadata over the authenticated Kubernetes TLS tunnel; it does not disable TLS verification or change BAS settings. A real PKB-to-BAS introspection failure remains a promotion-blocking test failure.

### Verification evidence (2026-10-24)

Local CI classifier, pipeline dependency and E2E prerequisite tests passed through `./gradlew test`. A verified, non-staff synthetic account was created in testing BAS and its credentials were saved to GitHub's `testing` environment. The live deployed suite reached BAS successfully: health/OpenAPI and unauthenticated-denial checks passed, while authenticated PKB checks failed with `401`. PKB logs identify an upstream BAS introspection `401`; a boolean-only BAS settings check confirms that `pkb-service` is absent from its internal-client registry. Production promotion must remain blocked until the directed PKB/BAS service credential configuration is repaired and this gate passes. No workload rollout or production credential mutation was performed for this change.

A companion [central architecture patch](architecture-sync/pkb-66-test-prod-promotion.patch) records the pipeline as **proposed**, preserves existing Helm ownership/manual production authorization, and includes release metadata. It is prepared for central-owner review; it has not been applied to the architecture repository.

## Configure Secrets

Before enabling protected PKB endpoints, register PKB as an internal client in
the BioCompass auth service `AUTH_INTERNAL_CLIENTS_JSON`, for example
`{"pkb-service":"<pkb-introspection-secret>"}`. The PKB deployment must receive
the same value as `PKB_AUTH_INTROSPECTION_SERVICE_TOKEN`; it uses that secret
when calling `POST /api/v1/internal/auth/token/introspect/`.

Create a real secret through Helm values before deploying to an environment with backing services:

```sh
helm upgrade --install pkb-service "${HELM_CHART}" \
  --namespace bio-compass \
  --set image.repository=pkb-service \
  --set image.pullPolicy=IfNotPresent \
  --set image.tag="${IMAGE_TAG}" \
  --set secrets.create=true \
  --set secrets.stringData.PKB_AUTH_INTROSPECTION_SERVICE_TOKEN='<pkb-introspection-secret>' \
  --set secrets.stringData.PKB_DATASOURCE_PASSWORD='<postgres-password>' \
  --set secrets.stringData.PKB_S3_ACCESS_KEY='<s3-access-key>' \
  --set secrets.stringData.PKB_S3_SECRET_KEY='<s3-secret-key>'
```

The baseline deployment marks the secret reference as optional so the scaffold can start before PostgreSQL, Kafka, and object storage are deployed. Later implementation steps should make required secrets explicit when those dependencies are mandatory.

## Deploy

Install or upgrade the chart with local image settings:

```sh
helm upgrade --install pkb-service "${HELM_CHART}" \
  --namespace bio-compass \
  --set image.repository=pkb-service \
  --set image.pullPolicy=IfNotPresent \
  --set image.tag="${IMAGE_TAG}"
```

If the image is published to GHCR, deploy that registry image:

```sh
helm upgrade --install pkb-service "${HELM_CHART}" \
  --namespace bio-compass \
  --set image.repository=ghcr.io/bio-compass/pkb-service \
  --set image.tag="${IMAGE_TAG}"
```

Wait for rollout:

```sh
kubectl rollout status deployment/pkb-service -n bio-compass
```

## Verify

Inspect pods and service routing:

```sh
helm status pkb-service -n bio-compass
kubectl get pods -n bio-compass
kubectl get svc -n bio-compass
kubectl describe deployment/pkb-service -n bio-compass
```

The PKB chart deploys MinIO as a private, single-replica StatefulSet with a persistent 20Gi `ReadWriteOnce` volume. Verify it and the PKB bucket initialization before registering artifacts:

```sh
kubectl rollout status statefulset/minio-pkb-service -n bio-compass --timeout=180s
kubectl get service/minio-pkb-service,statefulset/minio-pkb-service,pvc -n bio-compass
kubectl logs deployment/pkb-service -n bio-compass -c initialize-minio-bucket --tail=50
```

Forward the service locally and verify the health endpoint:

```sh
kubectl port-forward -n bio-compass svc/pkb-service 8080:80
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/health/liveness
```

Check logs:

```sh
kubectl logs -n bio-compass deployment/pkb-service
```

## Roll Back

View rollout history:

```sh
kubectl rollout history deployment/pkb-service -n bio-compass
```

Roll back to the previous revision:

```sh
kubectl rollout undo deployment/pkb-service -n bio-compass
kubectl rollout status deployment/pkb-service -n bio-compass
```

## Remove

Delete the baseline deployment:

```sh
helm uninstall pkb-service -n bio-compass
```
