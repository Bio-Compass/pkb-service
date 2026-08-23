#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: classify-deploy-change.sh --helm-diff FILE --diff-status STATUS --changed-files FILE --output-dir DIR
USAGE
}

helm_diff=""
diff_status=""
changed_files=""
output_dir=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --helm-diff)
      helm_diff="$2"
      shift 2
      ;;
    --diff-status)
      diff_status="$2"
      shift 2
      ;;
    --changed-files)
      changed_files="$2"
      shift 2
      ;;
    --output-dir)
      output_dir="$2"
      shift 2
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [ -z "${helm_diff}" ] || [ -z "${diff_status}" ] || [ -z "${changed_files}" ] || [ -z "${output_dir}" ]; then
  usage
  exit 2
fi

if [ ! -f "${helm_diff}" ]; then
  echo "Helm diff file ${helm_diff} was not found." >&2
  exit 1
fi

if [ ! -f "${changed_files}" ]; then
  echo "Changed files list ${changed_files} was not found." >&2
  exit 1
fi

mkdir -p "${output_dir}"

non_image_diff="${output_dir}/non-image-diff.txt"
source_approval_files="${output_dir}/source-approval-files.txt"
manual_approval_review="${output_dir}/manual-approval-review.md"
: > "${non_image_diff}"
: > "${source_approval_files}"

helm_requires_approval=false
source_requires_approval=false

if [ "${diff_status}" != "0" ]; then
  awk '
    /,[[:space:]]*Secret[[:space:]]*[(][^)]*[)][[:space:]]*(has changed|has been added|has been removed):/ {
      print
      next
    }
    /^(\+\+\+|---)([[:space:]]|$)/ { next }
    /^[+-]/ {
      content = substr($0, 2)
      if (content ~ /^[[:space:]]*image:[[:space:]]/) {
        next
      }
      print
    }
  ' "${helm_diff}" > "${non_image_diff}"

  if [ -s "${non_image_diff}" ]; then
    helm_requires_approval=true
  fi
fi

# A source change to runtime or deploy-control files is not image-only from a release-control perspective,
# even if the rendered Kubernetes diff only changes the image tag.
runtime_or_deploy_control_path='^(\.github/workflows/|\.github/scripts/|build\.gradle$|settings\.gradle$|gradle\.properties$|gradlew$|gradlew\.bat$|gradle/wrapper/|src/|deploy/|compose\.yaml$|\.env\.example$)'

grep -E "${runtime_or_deploy_control_path}" "${changed_files}" > "${source_approval_files}" || true

if [ -s "${source_approval_files}" ]; then
  source_requires_approval=true
fi

requires_approval=false
image_only=true

if [ "${helm_requires_approval}" = "true" ] || [ "${source_requires_approval}" = "true" ]; then
  requires_approval=true
  image_only=false
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "image-only=${image_only}"
    echo "requires-approval=${requires_approval}"
    echo "helm-requires-approval=${helm_requires_approval}"
    echo "source-requires-approval=${source_requires_approval}"
  } >> "${GITHUB_OUTPUT}"
fi

if [ "${requires_approval}" = "true" ]; then
  {
    echo "### Manual approval review"
    echo
    echo "Review this before approving the dev-manual-approval environment."
    echo
    echo "- Helm diff requires approval: ${helm_requires_approval}"
    echo "- Source/runtime files require approval: ${source_requires_approval}"
    echo

    if [ "${source_requires_approval}" = "true" ]; then
      echo "#### Source/runtime files requiring approval"
      echo
      echo "The push changes service runtime or deployment-control files, so the deploy is not treated as image-only."
      echo
      echo '```'
      head -c 20000 "${source_approval_files}"
      echo
      echo '```'
      echo
    fi

    echo "#### Full Helm diff"
    echo
    if [ -s "${helm_diff}" ]; then
      echo '```diff'
      head -c 60000 "${helm_diff}"
      echo
      echo '```'
    else
      echo "No rendered Helm diff was produced."
    fi
    echo

    if [ "${helm_requires_approval}" = "true" ]; then
      echo "#### Non-image Helm diff"
      echo
      echo "The Helm diff includes changes beyond container image updates."
      echo
      echo '```diff'
      head -c 20000 "${non_image_diff}"
      echo
      echo '```'
    fi
  } > "${manual_approval_review}"

  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat "${manual_approval_review}" >> "${GITHUB_STEP_SUMMARY}"
  fi
fi
