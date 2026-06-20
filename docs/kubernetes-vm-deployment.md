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

## GitHub Actions Deployment

On pushes to `main`, the repository CI workflow runs tests, pushes the service image to `ghcr.io/bio-compass/pkb-service`, reads the Helm chart from the OCI chart registry, shows the Helm diff, then deploys with `helm upgrade --install`.

```sh
helm upgrade --install pkb-service oci://ghcr.io/bio-compass/charts/pkb-service \
  --namespace bio-compass \
  --set image.repository=ghcr.io/bio-compass/pkb-service \
  --set image.tag=<image-tag>
```

The plan job writes the Helm diff to the job log and step summary. If the diff only changes container image lines, the workflow deploys automatically. If the diff includes any non-image change, the workflow waits on the `dev-manual-approval` GitHub environment before deploying.

Configure `dev-manual-approval` with required reviewers in GitHub Environments to enforce the manual approval gate.

The deploy jobs require credentials in the GitHub `dev` environment or repository secrets:

- `BIO_COMPASS_HELM_TOKEN`: token with package read access to the Helm chart registry when the default `GITHUB_TOKEN` cannot read the chart package. For GHCR, use a personal access token classic with `read:packages`, or grant the `pkb-service` repository access to the chart package so `GITHUB_TOKEN` can read it.
- `KUBE_CONFIG`: raw kubeconfig content for the target cluster.
- `KUBE_CONFIG_B64`: base64-encoded kubeconfig content. This is only used when `KUBE_CONFIG` is not set.

The Helm chart reference can be overridden with repository variables:

- `PKB_SERVICE_HELM_CHART`: OCI chart reference. Defaults to `oci://ghcr.io/bio-compass/charts/pkb-service`.
- `PKB_SERVICE_HELM_CHART_VERSION`: optional chart version.
- `HELM_VERSION`: optional Helm CLI version for deployment jobs. Defaults to `v3.19.2`.
- `HELM_DIFF_VERSION`: optional pinned `databus23/helm-diff` plugin version. Defaults to `v3.13.2`.
- `HELM_REGISTRY_USERNAME`: optional username for Helm registry login. Defaults to the workflow actor. Set this when `BIO_COMPASS_HELM_TOKEN` belongs to a different GitHub user.

The workflow waits for `deployment/pkb-service` to roll out in the `bio-compass` namespace after Helm upgrade.

## Configure Secrets

Create a real secret through Helm values before deploying to an environment with backing services:

```sh
helm upgrade --install pkb-service "${HELM_CHART}" \
  --namespace bio-compass \
  --set image.repository=pkb-service \
  --set image.pullPolicy=IfNotPresent \
  --set image.tag="${IMAGE_TAG}" \
  --set secrets.create=true \
  --set secrets.stringData.PKB_DATASOURCE_PASSWORD='<postgres-password>' \
  --set secrets.stringData.PKB_S3_ACCESS_KEY='<s3-access-key>' \
  --set secrets.stringData.PKB_S3_SECRET_KEY='<s3-secret-key>'
```

The baseline deployment marks the secret reference as optional so the scaffold can start before PostgreSQL, Kafka, OPA, and object storage are deployed. Later implementation steps should make required secrets explicit when those dependencies are mandatory.

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
