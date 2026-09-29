#!/usr/bin/env bash
set -euo pipefail

: "${BASE_URL:?Set BASE_URL to the deployed backend}"
: "${INTERNAL_API_KEY:?Set INTERNAL_API_KEY}"
: "${LOADTEST_TOKEN:?Set LOADTEST_TOKEN}"
for executable in curl jq k6; do
  command -v "$executable" >/dev/null || { echo "Missing: $executable" >&2; exit 1; }
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
batch_count=${BATCH_COUNT:-10}
groups_per_batch=${GROUPS_PER_BATCH:-10}
demands_per_group=${DEMANDS_PER_GROUP:-100}
verify_timeout=${VERIFY_TIMEOUT_SECONDS:-1800}
for value in "$batch_count" "$groups_per_batch" "$demands_per_group" "$verify_timeout"; do
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo 'Counts and timeout must be positive integers' >&2; exit 1; }
done
(( batch_count <= 100 )) || { echo 'BATCH_COUNT must be at most 100' >&2; exit 1; }
(( groups_per_batch <= 1000 )) || { echo 'GROUPS_PER_BATCH must be at most 1000' >&2; exit 1; }
(( demands_per_group <= 10000 )) || { echo 'DEMANDS_PER_GROUP must be at most 10000' >&2; exit 1; }
(( groups_per_batch * demands_per_group <= 10000 )) \
  || { echo 'Each seed batch must contain at most 10000 demands' >&2; exit 1; }

total_orders=$((batch_count * groups_per_batch * demands_per_group))
result_root=${RESULTS_DIR:-"$script_dir/results"}
mkdir -p "$result_root"
output_dir=$(mktemp -d "$result_root/order-load-test-batched.XXXXXX")
echo "Results: $output_dir"
echo "Seeding $batch_count runs; expected total orders: $total_orders"
base=${BASE_URL%/}/api/load-tests/internal/orders
payload=$(jq -n --argjson groups "$groups_per_batch" --argjson demands "$demands_per_group" \
  '{groups:$groups,demandsPerGroup:$demands}')
workload="$output_dir/workload.json"

for batch in $(seq 1 "$batch_count"); do
  manifest=$(printf '%s/manifest-%03d.json' "$output_dir" "$batch")
  echo "Seed batch $batch/$batch_count"
  if ! curl --fail-with-body --silent --show-error --max-time 600 \
    -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "x-loadtest: $LOADTEST_TOKEN" \
    -H 'Content-Type: application/json' --data "$payload" "$base/seed" -o "$manifest"; then
    echo "Seed batch $batch failed. Do not retry blindly; inspect $manifest and server state." >&2
    echo "Successfully returned manifests remain in $output_dir." >&2
    exit 1
  fi
  jq -e '.runId and (.products | length > 0)' "$manifest" >/dev/null
  jq -s '{runs:.}' "$output_dir"/manifest-*.json > "$workload"
done

jq -e --argjson expected "$total_orders" '
  ([.runs[].products[].demands[]] | length) == $expected
' "$workload" >/dev/null

load_status=0
MANIFEST="$workload" k6 run --include-system-env-vars=true \
  --summary-export "$output_dir/k6-summary.json" \
  "$script_dir/order-create-batched.js" || load_status=$?

deadline=$((SECONDS + verify_timeout))
while true; do
  all_passed=true
  batch=0
  for manifest in "$output_dir"/manifest-*.json; do
    batch=$((batch + 1))
    verification=$(printf '%s/verification-%03d.json' "$output_dir" "$batch")
    curl --fail-with-body --silent --show-error --max-time 300 \
      -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "x-loadtest: $LOADTEST_TOKEN" \
      -H 'Content-Type: application/json' --data-binary "@$manifest" \
      "$base/verify" -o "$verification"
    if ! jq -e '.passed == true' "$verification" >/dev/null; then
      all_passed=false
    fi
  done

  jq -s '{
    passed: all(.[]; .passed == true),
    expectedOrders: (map(.expectedOrders) | add),
    actualOrders: (map(.actualOrders) | add),
    violations: (map(.violations | length) | add)
  }' "$output_dir"/verification-*.json > "$output_dir/verification-summary.json"

  if [[ "$all_passed" == true ]]; then
    cat "$output_dir/verification-summary.json"
    exit "$load_status"
  fi
  if (( SECONDS >= deadline )); then
    echo "Order verification failed. See $output_dir/verification-summary.json and per-run files." >&2
    cat "$output_dir/verification-summary.json" >&2
    exit 1
  fi
  sleep 10
done
