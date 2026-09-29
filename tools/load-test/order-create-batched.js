import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

if (!__ENV.MANIFEST || !__ENV.BASE_URL || !__ENV.INTERNAL_API_KEY || !__ENV.LOADTEST_TOKEN) {
  throw new Error('MANIFEST, BASE_URL, INTERNAL_API_KEY, LOADTEST_TOKEN are required');
}

const workload = JSON.parse(open(__ENV.MANIFEST));
if (!Array.isArray(workload.runs) || workload.runs.length < 1) {
  throw new Error('Batched manifest must contain at least one run');
}
const products = workload.runs.flatMap(run => {
  if (!run.runId || !Array.isArray(run.products) || run.products.length < 1) {
    throw new Error('Every run must contain runId and products');
  }
  return run.products.map(product => ({ runId: run.runId, productId: product.productId }));
});
if (new Set(products.map(product => `${product.runId}:${product.productId}`)).size !== products.length) {
  throw new Error('Batched manifest must contain distinct run/product pairs');
}

const vus = Number(__ENV.LOAD_VUS || 50);
if (!Number.isInteger(vus) || vus < 1) throw new Error('LOAD_VUS must be positive');

export const options = {
  scenarios: {
    order_creation: {
      executor: 'shared-iterations',
      vus: Math.min(vus, products.length),
      iterations: products.length,
      maxDuration: __ENV.LOAD_MAX_DURATION || '15m',
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
    `${base}/api/load-tests/internal/orders/${product.runId}/products/${product.productId}`,
    null,
    { headers: {
      'X-Internal-Api-Key': __ENV.INTERNAL_API_KEY,
      'x-loadtest': __ENV.LOADTEST_TOKEN,
    },
      tags: { name: 'load-test-order-create-batched' }, timeout: '60s' },
  );
  check(response, {
    'group buy created': r => r.status === 200 && Number(r.json('groupBuyId')) > 0,
  });
}
