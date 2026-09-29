#!/usr/bin/env bash
set -euo pipefail

: "${BASE_URL:?Set BASE_URL to the deployed backend}"
: "${INTERNAL_API_KEY:?Set INTERNAL_API_KEY}"
: "${LOADTEST_TOKEN:?Set LOADTEST_TOKEN}"
workload=${1:?Usage: cleanup-orders-batched.sh /path/to/workload.json}
for executable in curl jq; do
  command -v "$executable" >/dev/null || { echo "Missing: $executable" >&2; exit 1; }
done
[[ -f "$workload" ]] || { echo "Workload not found: $workload" >&2; exit 1; }
run_count=$(jq -er '.runs | length | select(. > 0)' "$workload")
confirmation_text="DELETE $run_count RUNS"
read -r -p "Delete all $run_count load-test runs? Type '$confirmation_text': " confirmation
[[ "$confirmation" == "$confirmation_text" ]] || { echo 'Canceled: confirmation did not match.'; exit 1; }

mapfile -t run_ids < <(jq -er '.runs[].runId' "$workload")
status=0
chunk_size=5
for ((offset = 0; offset < run_count; offset += chunk_size)); do
  chunk=("${run_ids[@]:offset:chunk_size}")
  payload=$(printf '%s\n' "${chunk[@]}" | jq -R . | jq -s '{runIds:.}')
  echo "Cleaning runs $((offset + 1))-$((offset + ${#chunk[@]}))/$run_count"
  response=''
  if ! response=$(curl --fail-with-body --silent --show-error --max-time 600 \
    -X POST -H "X-Internal-Api-Key: $INTERNAL_API_KEY" \
    -H "x-loadtest: $LOADTEST_TOKEN" \
    -H 'Content-Type: application/json' --data "$payload" \
    "${BASE_URL%/}/api/load-tests/internal/orders/cleanup-bulk"); then
    echo "$response" >&2
    status=1
    continue
  fi
  jq . <<<"$response"
done
exit "$status"
