#!/usr/bin/env bash
set -euo pipefail

: "${BASE_URL:?Set BASE_URL to the deployed backend}"
: "${INTERNAL_API_KEY:?Set INTERNAL_API_KEY}"
: "${LOADTEST_TOKEN:?Set LOADTEST_TOKEN}"
for executable in curl jq k6; do
  command -v "$executable" >/dev/null || { echo "Missing: $executable" >&2; exit 1; }
done
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
group_count=${GROUP_COUNT:-10}
demands_per_group=${DEMANDS_PER_GROUP:-10}
verify_timeout=${VERIFY_TIMEOUT_SECONDS:-300}
for value in "$group_count" "$demands_per_group" "$verify_timeout"; do
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo 'Counts and timeout must be positive integers' >&2; exit 1; }
done
result_root=${RESULTS_DIR:-"$script_dir/results"}
mkdir -p "$result_root"
output_dir=$(mktemp -d "$result_root/order-load-test.XXXXXX")
echo "Results: $output_dir"
base=${BASE_URL%/}/api/load-tests/internal/orders
payload=$(jq -n --argjson groups "$group_count" --argjson demands "$demands_per_group" \
  '{groups:$groups,demandsPerGroup:$demands}')

# 재시도하지 않는다. 응답 유실 시 서버 데이터가 이미 생성됐을 수 있다.
curl --fail-with-body --silent --show-error --max-time 600 \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "x-loadtest: $LOADTEST_TOKEN" \
  -H 'Content-Type: application/json' \
  --data "$payload" "$base/seed" -o "$output_dir/manifest.json"
jq -e '.runId and (.products | length > 0)' "$output_dir/manifest.json" >/dev/null

load_status=0
MANIFEST="$output_dir/manifest.json" k6 run --include-system-env-vars=true \
  --summary-export "$output_dir/k6-summary.json" \
  "$script_dir/order-create.js" || load_status=$?

# 생성 요청 종료 후 대사한다. API 성공과 비동기 작업 완료를 구분한다.
deadline=$((SECONDS + verify_timeout))
while true; do
  curl --fail-with-body --silent --show-error --max-time 120 \
    -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -H "x-loadtest: $LOADTEST_TOKEN" \
    -H 'Content-Type: application/json' \
    --data-binary "@$output_dir/manifest.json" "$base/verify" -o "$output_dir/verification.json"
  if jq -e '.passed == true' "$output_dir/verification.json" >/dev/null; then
    jq '{passed,expectedOrders,actualOrders}' "$output_dir/verification.json"
    exit "$load_status"
  fi
  if (( SECONDS >= deadline )); then
    echo "Order verification failed. See $output_dir/verification.json" >&2
    exit 1
  fi
  sleep 10
done
