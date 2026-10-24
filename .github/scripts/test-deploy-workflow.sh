#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ruby "${script_dir}/test-deploy-workflow.rb"
python3 -B -m unittest discover -s "${script_dir}" -p 'test_deployed_e2e.py'
