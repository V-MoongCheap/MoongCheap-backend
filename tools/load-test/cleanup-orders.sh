#!/usr/bin/env bash
set -euo pipefail

: "${BASE_URL:?Set BASE_URL to the deployed backend}"
: "${INTERNAL_API_KEY:?Set INTERNAL_API_KEY}"
: "${LOADTEST_TOKEN:?Set LOADTEST_TOKEN}"
manifest=${1:?Usage: cleanup-orders.sh /path/to/manifest.json}
for executable in curl jq; do
  command -v "$executable" >/dev/null || { echo "Missing: $executable" >&2; exit 1; }
done
[[ -f "$manifest" ]] || { echo "Manifest not found: $manifest" >&2; exit 1; }
run_id=$(jq -er '.runId' "$manifest")
read -r -p "Delete load-test run $run_id only? Type the run ID: " confirmation
[[ "$confirmation" == "$run_id" ]] || { echo 'Canceled: run ID did not match.' >&2; exit 1; }

curl --fail-with-body --silent --show-error --max-time 600 \
  -X DELETE -H "X-Internal-Api-Key: $INTERNAL_API_KEY" \
  -H "x-loadtest: $LOADTEST_TOKEN" \
  "${BASE_URL%/}/api/load-tests/internal/orders/$run_id" | jq .
