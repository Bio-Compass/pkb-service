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
  local work_dir="${tmp_dir}/${name}"

  mkdir -p "${work_dir}"

  local github_output="${work_dir}/github-output.txt"
  local github_summary="${work_dir}/github-step-summary.md"
  GITHUB_OUTPUT="${github_output}" \
  GITHUB_STEP_SUMMARY="${github_summary}" \
    "${classifier}" \
      --helm-diff "${work_dir}/helm-diff.txt" \
      --diff-status "${diff_status}" \
      --output-dir "${work_dir}"

  assert_output "${name}" "${github_output}" "image-only" "${expected_image_only}"
  assert_output "${name}" "${github_output}" "requires-approval" "${expected_requires_approval}"
  assert_output "${name}" "${github_output}" "helm-requires-approval" "${expected_helm_requires_approval}"

  if [ "${expected_requires_approval}" = "true" ]; then
    assert_file_contains "${name}" "${work_dir}/manual-approval-review.md" "### Manual approval review"
    assert_file_contains "${name}" "${work_dir}/manual-approval-review.md" "#### Full Helm diff"
    assert_file_contains "${name}" "${github_summary}" "### Manual approval review"
  else
    if [ -e "${work_dir}/manual-approval-review.md" ]; then
      echo "${name}: manual approval review should not be created when approval is not required" >&2
      exit 1
    fi
  fi
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

assert_file_contains() {
  local name="$1"
  local file="$2"
  local pattern="$3"

  if [ ! -s "${file}" ]; then
    echo "${name}: expected ${file} to exist and be non-empty" >&2
    exit 1
  fi

  if ! grep -Fq "${pattern}" "${file}"; then
    echo "${name}: expected ${file} to contain ${pattern}" >&2
    exit 1
  fi
}

assert_file_does_not_contain() {
  local name="$1"
  local file="$2"
  local pattern="$3"

  if grep -Fq -- "${pattern}" "${file}"; then
    echo "${name}: expected ${file} not to contain ${pattern}" >&2
    exit 1
  fi
}

assert_file_does_not_contain \
  "workflow-classifier-integration" \
  "${script_dir}/../workflows/ci.yml" \
  "--changed-files"

case_dir="${tmp_dir}/image-only-helm"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Deployment (apps) has changed:
-        image: ghcr.io/bio-compass/pkb-service:main-old
+        image: ghcr.io/bio-compass/pkb-service:main-new
DIFF
run_case "image-only-helm" "2" "true" "false" "false"

case_dir="${tmp_dir}/non-image-helm"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Deployment (apps) has changed:
-        image: ghcr.io/bio-compass/pkb-service:main-old
+        image: ghcr.io/bio-compass/pkb-service:main-new
-        name: OLD_VALUE
+        name: NEW_VALUE
DIFF
run_case "non-image-helm" "2" "false" "true" "true"

case_dir="${tmp_dir}/datasource-password-helm"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Secret (v1) has changed:
-  PKB_DATASOURCE_PASSWORD: old-redacted
+  PKB_DATASOURCE_PASSWORD: new-redacted
DIFF
run_case "datasource-password-helm" "2" "false" "true" "true"
assert_file_contains "datasource-password-helm" "${tmp_dir}/datasource-password-helm/manual-approval-review.md" "PKB_DATASOURCE_PASSWORD"

case_dir="${tmp_dir}/suppressed-secret-helm"
mkdir -p "${case_dir}"
cat > "${case_dir}/helm-diff.txt" <<'DIFF'
bio-compass, pkb-service, Secret (v1) has changed:
  Secret data suppressed
DIFF
run_case "suppressed-secret-helm" "2" "false" "true" "true"
assert_file_contains "suppressed-secret-helm" "${tmp_dir}/suppressed-secret-helm/manual-approval-review.md" "Secret (v1) has changed"

case_dir="${tmp_dir}/no-helm-diff"
mkdir -p "${case_dir}"
touch "${case_dir}/helm-diff.txt"
run_case "no-helm-diff" "0" "true" "false" "false"

echo "CI deploy change classifier tests passed."
