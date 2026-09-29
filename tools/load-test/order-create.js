import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

if (!__ENV.MANIFEST || !__ENV.BASE_URL || !__ENV.INTERNAL_API_KEY) {
  throw new Error('MANIFEST, BASE_URL, INTERNAL_API_KEY are required');
}
const manifest = JSON.parse(open(__ENV.MANIFEST));
const products = manifest.products;
if (!products?.length || new Set(products.map(p => p.productId)).size !== products.length) {
  throw new Error('Manifest must contain distinct products');
}
const vus = Number(__ENV.LOAD_VUS || 5);
if (!Number.isInteger(vus) || vus < 1) throw new Error('LOAD_VUS must be positive');

// 유한한 입력을 정확히 한 번 배정한다. 고정 RPS 시험이 아닌 동시성 기반 burst 시험이다.
export const options = {
  scenarios: {
    order_creation: {
      executor: 'shared-iterations',
      vus: Math.min(vus, products.length),
      iterations: products.length,
      maxDuration: __ENV.LOAD_MAX_DURATION || '5m',
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    iterations: [`count==${products.length}`],
  },
};

export default function () {
  const product = products[exec.scenario.iterationInTest];
  const base = __ENV.BASE_URL.replace(/\/$/, '');
  const response = http.post(
    `${base}/api/load-tests/internal/orders/${manifest.runId}/products/${product.productId}`,
    null,
    { headers: { 'X-Internal-Api-Key': __ENV.INTERNAL_API_KEY },
      tags: { name: 'load-test-order-create' }, timeout: '60s' },
  );
  check(response, {
    'group buy created': r => r.status === 200 && Number(r.json('groupBuyId')) > 0,
  });
}
