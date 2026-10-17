#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workflow="${script_dir}/../workflows/ci.yml"

assert_occurrences() {
  local expected="$1"
  local pattern="$2"
  local actual

  actual="$(grep -F -c -- "${pattern}" "${workflow}" || true)"
  if [ "${actual}" -ne "${expected}" ]; then
    echo "Expected ${expected} occurrences of ${pattern}, found ${actual}." >&2
    exit 1
  fi
}

# The plan, image-only deploy, and approved deploy jobs must all send the
# GitHub S3 secrets to Helm without expanding them into the command line.
assert_occurrences 4 'PKB_S3_ACCESS_KEY: ${{ secrets.PKB_S3_ACCESS_KEY }}'
assert_occurrences 4 'PKB_S3_SECRET_KEY: ${{ secrets.PKB_S3_SECRET_KEY }}'
assert_occurrences 3 's3_access_key_file="$(mktemp)"'
assert_occurrences 3 's3_secret_key_file="$(mktemp)"'
assert_occurrences 3 'printf '\''%s'\'' "${PKB_S3_ACCESS_KEY}" > "${s3_access_key_file}"'
assert_occurrences 3 'printf '\''%s'\'' "${PKB_S3_SECRET_KEY}" > "${s3_secret_key_file}"'
assert_occurrences 3 '--set-file secrets.stringData.PKB_S3_ACCESS_KEY="${s3_access_key_file}"'
assert_occurrences 3 '--set-file secrets.stringData.PKB_S3_SECRET_KEY="${s3_secret_key_file}"'

echo "CI deployment workflow S3 secret injection tests passed."
