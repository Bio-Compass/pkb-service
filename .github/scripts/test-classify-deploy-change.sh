#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
classifier="${script_dir}/classify-deploy-change.sh"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

run_case() {
  local name="$1"
  local diff_status="$2"
  local expected_image_only="$3"
  local expected_requires_approval="$4"
  local expected_helm_requires_approval="$5"
  local expected_source_requires_approval="$6"
  local work_dir="${tmp_dir}/${name}"

  mkdir -p "${work_dir}"

  local github_output="${work_dir}/github-output.txt"
  GITHUB_OUTPUT="${github_output}" \
    "${classifier}" \
      --helm-diff "${work_dir}/helm-diff.txt" \
      --diff-status "${diff_status}" \
      --changed-files "${work_dir}/changed-files.txt" \
      --output-dir "${work_dir}"

  assert_output "${name}" "${github_output}" "image-only" "${expected_image_only}"
  assert_output "${name}" "${github_output}" "requires-approval" "${expected_requires_approval}"
  assert_output "${name}" "${github_output}" "helm-requires-approval" "${expected_helm_requires_approval}"
  assert_output "${name}" "${github_output}" "source-requires-approval" "${expected_source_requires_approval}"
}

assert_output() {
  local name="$1"
  local output_file="$2"
  local key="$3"
  local expected="$4"
  local actual

  actual="$(grep -E "^${key}=" "${output_file}" | cut -d= -f2-)"
  if [ "${actual}" != "${expected}" ]; then
    echo "${name}: expected ${key}=${expected}, got ${actual}" >&2
    exit 1
  fi
}

case_dir="${tmp_dir}/image-only-helm"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Deployment (apps) has changed:
-        image: ghcr.io/bio-compass/pkb-service:main-old
+        image: ghcr.io/bio-compass/pkb-service:main-new
DIFF
cat > "${case_dir}/changed-files.txt" <<'FILES'
docs/kubernetes-vm-deployment.md
FILES
run_case "image-only-helm" "2" "true" "false" "false" "false"

case_dir="${tmp_dir}/non-image-helm"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Deployment (apps) has changed:
-        image: ghcr.io/bio-compass/pkb-service:main-old
+        image: ghcr.io/bio-compass/pkb-service:main-new
-        name: OLD_VALUE
+        name: NEW_VALUE
DIFF
cat > "${case_dir}/changed-files.txt" <<'FILES'
docs/kubernetes-vm-deployment.md
FILES
run_case "non-image-helm" "2" "false" "true" "true" "false"

case_dir="${tmp_dir}/source-runtime-change"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Deployment (apps) has changed:
-        image: ghcr.io/bio-compass/pkb-service:main-old
+        image: ghcr.io/bio-compass/pkb-service:main-new
DIFF
cat > "${case_dir}/changed-files.txt" <<'FILES'
src/main/java/com/biocompass/pkb/PkbServiceApplication.java
FILES
run_case "source-runtime-change" "2" "false" "true" "false" "true"

case_dir="${tmp_dir}/no-helm-diff-runtime-change"
mkdir -p "${case_dir}"
touch "${case_dir}/helm-diff.txt"
cat > "${case_dir}/changed-files.txt" <<'FILES'
.github/workflows/ci.yml
FILES
run_case "no-helm-diff-runtime-change" "0" "false" "true" "false" "true"

case_dir="${tmp_dir}/no-helm-diff-docs-change"
mkdir -p "${case_dir}"
touch "${case_dir}/helm-diff.txt"
cat > "${case_dir}/changed-files.txt" <<'FILES'
README.md
docs/local-verification-testcases.md
FILES
run_case "no-helm-diff-docs-change" "0" "true" "false" "false" "false"

echo "CI deploy change classifier tests passed."
