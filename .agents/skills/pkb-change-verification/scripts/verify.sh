#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"

case "$mode" in
  command) task=commandTest ;;
  api) task=commandApiTest ;;
  kafka) task=kafkaIngressTest ;;
  persistence) task=persistenceTest ;;
  artifact) task=artifactStorageTest ;;
  full) task=fullVerification ;;
  *)
    echo "Usage: $0 {command|api|kafka|persistence|artifact|full}" >&2
    exit 2
    ;;
esac

repository_root="$(git rev-parse --show-toplevel)"
cd "$repository_root"

./gradlew "$task" --no-daemon
git diff --check
