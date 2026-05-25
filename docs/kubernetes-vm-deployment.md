# VM-Hosted Kubernetes Deployment

This deployment path targets a single Linux virtual machine running k3s. The PKB service repository owns the Java service image, while the Helm chart lives in the separate `Bio-Compass/bio-compass-helm` repository under `charts/pkb-service`.

## Prerequisites

- A Linux VM with Docker or containerd access.
- k3s installed and reachable with `kubectl`.
- Helm 3 installed on the machine running deployment commands.
- Java 25 available on the build machine.
- Docker available on the build machine when building into a local Docker daemon.
- A checkout of `Bio-Compass/bio-compass-helm` for Helm chart source and environment values.

Verify cluster access:

```sh
kubectl get nodes
helm version --short
```

Set the Helm repository location:

```sh
HELM_REPO=/path/to/bio-compass-helm
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

Set the image repository and tag during Helm install or upgrade from the Helm repository:

```sh
helm upgrade --install pkb-service "${HELM_REPO}/charts/pkb-service" \
  --namespace bio-compass \
  --create-namespace \
  -f "${HELM_REPO}/values/dev/pkb-service.yaml" \
  --set image.repository=pkb-service \
  --set image.pullPolicy=IfNotPresent \
  --set image.tag="${IMAGE_TAG}"
```

For registry images, build and push with `-PcontainerImage=<registry>/pkb-service`, then set `image.repository=<registry>/pkb-service`. The BioCompass Helm defaults expect `ghcr.io/bio-compass/pkb-service`.

## Configure Secrets

Create a real secret through Helm values before deploying to an environment with backing services:

```sh
helm upgrade --install pkb-service "${HELM_REPO}/charts/pkb-service" \
  --namespace bio-compass \
  --create-namespace \
  -f "${HELM_REPO}/values/dev/pkb-service.yaml" \
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
helm upgrade --install pkb-service "${HELM_REPO}/charts/pkb-service" \
  --namespace bio-compass \
  --create-namespace \
  -f "${HELM_REPO}/values/dev/pkb-service.yaml" \
  --set image.repository=pkb-service \
  --set image.pullPolicy=IfNotPresent \
  --set image.tag="${IMAGE_TAG}"
```

If the image is published to GHCR, use the generic Helm repository deploy script:

```sh
DEPLOY_SOURCE=local "${HELM_REPO}/scripts/deploy.sh" dev pkb-service "${IMAGE_TAG}"
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
