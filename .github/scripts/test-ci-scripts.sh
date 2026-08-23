#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "${script_dir}/test-classify-deploy-change.sh"
bash "${script_dir}/test-deploy-workflow.sh"
