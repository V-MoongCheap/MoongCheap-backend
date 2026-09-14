#!/usr/bin/env bash
#
# 운영/스테이징 환경에서 product_catalog OpenSearch 인덱스와 별칭을 생성.
# 매핑/설정 정의는 src/main/resources/opensearch/product-catalog-index.json 파일과 동일 소스를 사용.
#
# 사용법:
#   ./scripts/opensearch/create-product-index.sh
#
# 환경변수:
#   OPENSEARCH_URL       기본: http://localhost:9200
#   OPENSEARCH_USER      선택: basic auth id (보안 플러그인 사용 시)
#   OPENSEARCH_PASS      선택: basic auth pw
#   OPENSEARCH_INSECURE  선택: "true"로 지정 시 TLS 인증서 검증을 스킵 (self-signed)
#
# 동작:
#   1) 인덱스가 이미 있으면 생성 스킵.
#   2) 별칭이 이미 있으면 별칭 부착 스킵.
#   3) 중간 단계라도 오류가 나면 즉시 종료 (set -e).

set -euo pipefail

INDEX="product_catalog_v1"
ALIAS="product_catalog"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SETTINGS_FILE="${REPO_ROOT}/src/main/resources/opensearch/product-catalog-index.json"

OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"

CURL_ARGS=(-sS)
if [[ "${OPENSEARCH_INSECURE:-}" == "true" ]]; then
  CURL_ARGS+=(-k)
fi
if [[ -n "${OPENSEARCH_USER:-}" ]]; then
  CURL_ARGS+=(-u "${OPENSEARCH_USER}:${OPENSEARCH_PASS:-}")
fi

if [[ ! -f "${SETTINGS_FILE}" ]]; then
  echo "ERROR: settings file not found: ${SETTINGS_FILE}" >&2
  exit 1
fi

echo "Target: ${OPENSEARCH_URL}"
echo "Index:  ${INDEX}"
echo "Alias:  ${ALIAS}"
echo

index_status=$(curl "${CURL_ARGS[@]}" -o /dev/null -w '%{http_code}' \
  -X HEAD "${OPENSEARCH_URL}/${INDEX}")

if [[ "${index_status}" == "200" ]]; then
  echo "Index '${INDEX}' already exists — skip create."
else
  echo "Creating index '${INDEX}'..."
  curl "${CURL_ARGS[@]}" -f \
    -X PUT "${OPENSEARCH_URL}/${INDEX}" \
    -H 'Content-Type: application/json' \
    --data-binary "@${SETTINGS_FILE}"
  echo
  echo "Index created."
fi

alias_status=$(curl "${CURL_ARGS[@]}" -o /dev/null -w '%{http_code}' \
  -X HEAD "${OPENSEARCH_URL}/${INDEX}/_alias/${ALIAS}")

if [[ "${alias_status}" == "200" ]]; then
  echo "Alias '${ALIAS}' already points to '${INDEX}' — skip alias attach."
else
  echo "Attaching alias '${ALIAS}' -> '${INDEX}'..."
  curl "${CURL_ARGS[@]}" -f \
    -X POST "${OPENSEARCH_URL}/_aliases" \
    -H 'Content-Type: application/json' \
    -d "{\"actions\":[{\"add\":{\"index\":\"${INDEX}\",\"alias\":\"${ALIAS}\"}}]}"
  echo
  echo "Alias attached."
fi

echo
echo "Done."
