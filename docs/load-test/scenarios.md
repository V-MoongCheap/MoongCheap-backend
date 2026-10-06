# 부하 테스트 시나리오

DAU peak **방어** 관점의 부하 테스트 정의서.
스트레스 테스트(파괴 지점 탐색)가 아니라, **예상 peak DAU 트래픽을 안정적으로 처리하는지 검증**하는 것이 목적.

---

## 공통 사항

### 도구
- **부하 발생**: [k6](https://k6.io/) (JavaScript 시나리오)
- **관측**: Grafana + Prometheus + `pg_stat_activity` / `pg_locks` / Redis `LLEN`
- **오케스트레이션**: Makefile / shell (시딩 → 부하 → 리셋 순차 실행)

### 성공 기준 (템플릿)
- 대상 예상 peak RPS에서 **p95 응답시간 ≤ 목표 SLO**
- **에러율 ≤ 0.1%** (5xx, connection failure 포함)
- 부하 종료 후 시스템이 정상 상태로 복귀 (락 잔류 없음)

각 시나리오에서 구체 값은 프로덕션 지표 확인 후 확정.

### 시딩 원칙
- 매 실행 전 **재현 가능한 시작 상태**로 리셋 (또는 새로 시딩)
- 부하 대상 외의 데이터는 최소한만 준비
- 실제 프로덕션 스키마 제약(FK, unique index)을 만족해야 함

---

## 시나리오 [4] 수요 참여 부하 테스트

### 목적
낙찰 예정 인기 보드에 동시에 몰리는 참여 요청을 안정적으로 처리하는지 검증. 특히 **`DemandBoard` 행의 pessimistic lock(`findByIdAndStatusInForUpdate`)** 이 얼마나 tail latency를 만드는지 파악.

### 대상 API
`POST /api/demand-boards/{demandBoardId}/join`

Request body (`DemandBoardJoinRequestDto`):
```json
{
  "payMethodId": 123,
  "quantity": 1,
  "isSubstitutable": false,
  "extraRequirement": null,
  "autoPaymentAgreed": true,
  "privacyCollectionAgreed": true,
  "privacyThirdPartyAgreed": true,
  "paymentAgencyTermsAgreed": true
}
```

인증: 세션 쿠키 (`SID`)

### 시나리오 흐름

```
[Setup — 시딩 SQL]
  - 테스트 유저 N명 + 각 유저의 세션 쿠키 발급
  - 테스트 유저 N명 + 각 유저의 활성 BrandPay 결제수단
  - 활성 DemandBoard M개 (status=GB_GATHERING, sale_end_at 충분히 미래)
  - 각 보드마다 서로 다른 ProductCatalog 참조
    (같은 catalog면 uq_demand_member_catalog_active 제약에 걸림)
      ↓
[k6: ramping-arrival-rate]
  - 유저 세션 풀에서 랜덤 선택
  - 보드 풀에서 랜덤 선택 (또는 특정 hot 보드에 집중 옵션)
  - POST /api/demand-boards/{boardId}/join
  - 응답시간/에러율 기록
      ↓
[Teardown]
  - Demand 테이블 정리 (테스트 유저 memberId 범위)
  - 필요시 DemandBoard participant_count 리셋
```

### 측정 지표
| 지표 | 관심사 |
|------|--------|
| HTTP p50/p95/p99 | 사용자 체감 응답성 |
| HTTP 에러율 (4xx/5xx) | 실패 요청 비율. 특히 `409` (uq 제약) 는 시딩 문제 |
| `demand_board` 행 락 대기 시간 | `pg_stat_activity` 의 wait_event |
| DB 커넥션 풀 소진 여부 | HikariCP metrics |
| Tomcat 스레드 사용률 | server thread pool metrics |

### 필요 데이터

| 항목 | 최소 수량 | 비고 |
|------|-----------|------|
| Member (테스트 유저) | 예상 peak RPS × 시나리오 지속 시간 (초) / 유저당 join 횟수 | 유저당 여러 보드 join 가능하나 같은 catalog는 안 됨 |
| Session (Redis) | 유저 수만큼 | `SessionTestHelper.loginAs` 방식으로 사전 발급 |
| BrandPayMethod | 유저 수만큼, status=ACTIVE | `brandPayMethodRepository.existsByIdAndMemberIdAndStatus` 통과용 |
| ProductCatalog | 보드 수만큼 (또는 그 이상) | 각 보드가 다른 catalog 참조 |
| DemandBoard | 최소 10~50개 (핫스팟 시나리오면 1~3개) | status=GB_GATHERING, sale_end_at = NOW() + 1일 |
| Product (보드당 후보) | 이 시나리오에선 **불필요** | 참여 단계에는 product 참조 없음 |

**규모 예시** (예상 peak = 200 join RPS × 3분 = 36,000 requests):
- 각 유저가 평균 2보드 참여 → 유저 18,000명
- 보드 30개 (하나당 평균 1,200 참여)
- catalog 30개, 각 유저는 30개 중 임의 2개 선택

**핫스팟 옵션** (락 경합 관찰):
- 보드 3개만 사용, 유저 18,000명이 이 3개에만 집중 → 보드당 6,000 참여, 락 경합 최악 케이스

### 시딩 SQL (뼈대)

```sql
-- 테스트 데이터 격리를 위해 member_id 범위를 예약 (예: 900000000+)
INSERT INTO product_catalog (id, name, ...)
SELECT 900000000 + gs, 'test_catalog_' || gs, ...
FROM generate_series(1, 30) gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO member (id, ...)
SELECT 900000000 + gs, ... 
FROM generate_series(1, 18000) gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO brand_pay_method (id, member_id, status, ...)
SELECT 900000000 + gs, 900000000 + gs, 'ACTIVE', ...
FROM generate_series(1, 18000) gs
ON CONFLICT DO NOTHING;

INSERT INTO demand_board 
  (id, catalog_id, price_min, price_max, participant_count, status, sale_end_at, ...)
SELECT 
  900000000 + gs,
  900000000 + gs,
  10000, 20000, 0,
  'GB_GATHERING',
  NOW() + INTERVAL '1 day',
  ...
FROM generate_series(1, 30) gs
ON CONFLICT DO NOTHING;

-- 세션은 Redis에 SessionTestHelper 로직으로 별도 발급
-- 또는 shell에서 로그인 API를 사전 호출
```

### 테스트 위치: **내부 (Cloudflare 뒤)**

- **이유**: 순수 백엔드 성능(락 경합, DB, JVM)을 측정하는 게 목적. Cloudflare 앞단에서 쏘면 rate limit / bot protection / 지리적 지연이 노이즈로 들어감.
- **환경**: staging 클러스터의 K8s 내부 또는 동일 VPC의 별도 인스턴스에서 k6 실행.
- **주의**: Cloudflare 앞단 테스트가 필요한 경우(예: 실사용자 체감 검증)는 별도 후속 테스트로 분리. 이때는 Cloudflare 팀과 사전 조율 필요.

---

## 시나리오 [8] AI 낙찰 결과 반영 부하 테스트

### 목적
낙찰 시점에 AI가 다수 보드의 결과를 콜백으로 반영할 때, **write path (다중 UPDATE + INSERT + Redis LPUSH)가 안정적으로 처리되는지** 검증. DAU peak의 write burst 대응력 확인.

### 대상 API
`POST /api/awarding/internal/result`

Request body (`AwardingResultRequestDto`):
```json
{
  "schemaVersion": "awarding-result.v0.1",
  "plannedAt": "2026-01-01T00:00:00Z",
  "ruleVersion": "test-v1",
  "results": [
    {
      "boardId": 900000001,
      "judgedAt": "2026-01-01T00:00:00",
      "evaluations": [
        { "productId": 900010001, "score": 0.87, "reason": "test", "isAwarded": true },
        { "productId": 900010002, "score": 0.42, "reason": "test", "isAwarded": false }
      ]
    }
  ]
}
```

인증: HTTP header `X-Internal-Api-Key: <internalApiKey>` (`InternalApiKeyFilter`)

**제약**:
- `results` 최대 50개 (1 요청당)
- boardId 중복 불가
- 보드마다 evaluations 중 `isAwarded=true`는 최대 1개
- productId 중복 불가

### 시나리오 흐름

```
[Setup — 시딩 SQL]
  - Seller, ProductCatalog 등 참조 데이터
  - DemandBoard N개 (status=GB_AWARDING)
  - 각 보드마다 Demand 참여자 M명 (status=ASSIGNED, demand_board_id=?)
  - 각 보드마다 Product 후보 K개 (status=AWARDING)
      ↓
[k6: setup()]
  - GET /api/awarding/internal/pending?size=100 로 boardIds 확보
  - (또는 shell에서 SQL로 뽑아 --env 로 주입)
      ↓
[k6: ramping-arrival-rate]
  - Worker VU가 boardIds 소비하며 
    POST /api/awarding/internal/result 를 burst
  - Payload: 미리 만든 템플릿 로테이션
    (winner 있음 / winner 없음 / 평가 개수 다양)
  - 응답시간/에러율/트랜잭션 시간 기록
      ↓
[Teardown / 재실행 준비]
  - UPDATE demand_board SET status='GB_AWARDING' WHERE id IN <시딩 범위>
  - UPDATE demand SET status='ASSIGNED' WHERE demand_board_id IN <...>
  - UPDATE product SET status='AWARDING' WHERE demand_board_id IN <...>
  - DELETE FROM group_buy WHERE created_from_test = true (또는 시딩 범위)
  - DELETE FROM product_award_evaluation WHERE demand_board_id IN <...>
  - Redis: DEL group_buy_outbox payment_outbox
```

### 측정 지표
| 지표 | 관심사 |
|------|--------|
| HTTP p50/p95/p99 | 응답성 |
| HTTP 에러율 | 5xx, `DEMAND_BOARD_NOT_FOUND`(중복 처리), `DEMAND_BOARD_AWARDING_INCONSISTENT` 등 |
| DB 트랜잭션 시간 | `applyAwardingResult` 내부 원자적 트랜잭션 소요시간 |
| Redis `LLEN group_buy_outbox` | 콜백 처리로 쌓이는 outbox 길이 (consumer가 소화하는지) |
| Redis `LLEN payment_outbox` | 자동결제 outbox 길이 (하류 병목 파악) |
| DB 락 대기 | `demand_board`, `product` 다중 UPDATE 시 wait_event |

### 필요 데이터

| 항목 | 최소 수량 | 비고 |
|------|-----------|------|
| Seller | 후보 상품이 참조할 판매자. 1~수십명 | status=APPROVED |
| ProductCatalog | 보드가 참조. 1~수십개 | |
| DemandBoard | 예상 burst 규모만큼 | **status=GB_AWARDING**, sale_end_at 과거 |
| Demand (per board) | 실제 참여자 분포 (예: 20~200명) | status=ASSIGNED, demand_board_id 세팅 |
| Product (per board) | 낙찰 후보. 보통 3~5개 | **status=AWARDING**, demand_board_id 세팅 |
| Member | Demand 소유자용. Demand 수만큼 | 실제 로그인 불필요 (콜백은 내부 API) |
| BrandPayMethod | Demand의 pay_method_id 참조 | Demand 수만큼, status=ACTIVE |
| Internal API Key | 1개 | `moongcheap.security.internal-api-key` 값 |

**규모 예시** (예상 peak = 초당 50 result 콜백 × 60초):
- DemandBoard 3,000개 (GB_AWARDING)
- Demand 3,000 × 50 = 150,000 rows
- Product 3,000 × 4 = 12,000 rows
- 요청 1건에 최대 50 boards 담을 수 있으므로 60 요청/초 이하로 조절 가능

### 시딩 SQL (뼈대)

```sql
-- Seller
INSERT INTO seller (id, member_id, status, business_name, ...)
VALUES (900000001, ..., 'APPROVED', 'test_seller', ...)
ON CONFLICT DO NOTHING;

-- ProductCatalog
INSERT INTO product_catalog (id, name, status, ...)
SELECT 900000000 + gs, 'test_catalog_' || gs, 'ACTIVE', ...
FROM generate_series(1, 100) gs
ON CONFLICT DO NOTHING;

-- Member + BrandPayMethod (Demand 소유자용)
INSERT INTO member (id, ...) 
SELECT 900000000 + gs, ... 
FROM generate_series(1, 150000) gs 
ON CONFLICT DO NOTHING;

INSERT INTO brand_pay_method (id, member_id, status, ...)
SELECT 900000000 + gs, 900000000 + gs, 'ACTIVE', ...
FROM generate_series(1, 150000) gs
ON CONFLICT DO NOTHING;

-- DemandBoard (GB_AWARDING 상태)
INSERT INTO demand_board 
  (id, catalog_id, price_min, price_max, participant_count, status, sale_end_at, ...)
SELECT 
  900000000 + gs,
  900000000 + ((gs - 1) % 100 + 1),  -- 100개 catalog 순환
  10000, 20000, 50,
  'GB_AWARDING',
  NOW() - INTERVAL '1 minute',
  ...
FROM generate_series(1, 3000) gs
ON CONFLICT DO NOTHING;

-- Demand (보드당 50명씩)
INSERT INTO demand 
  (member_id, catalog_id, demand_board_id, pay_method_id, 
   desired_price_min, desired_price_max, desire_end_at, 
   quantity, is_substitutable, status, ...)
SELECT 
  900000000 + ((db.id - 900000000 - 1) * 50 + p),
  db.catalog_id,
  db.id,
  900000000 + ((db.id - 900000000 - 1) * 50 + p),
  10000, 20000,
  NOW() + INTERVAL '1 day',
  1, false,
  'ASSIGNED',
  ...
FROM (SELECT id, catalog_id FROM demand_board 
       WHERE id BETWEEN 900000001 AND 900003000) db,
     generate_series(1, 50) p
ON CONFLICT DO NOTHING;

-- Product (보드당 4개 후보)
INSERT INTO product 
  (id, catalog_id, demand_board_id, seller_id, 
   unit_price, shipping_fee, delivery_date, sale_end_at,
   total_quantity, min_participant_count, min_quantity, 
   return_policy, status, ...)
SELECT 
  900010000 + (db.id - 900000000 - 1) * 4 + p,
  db.catalog_id,
  db.id,
  900000001,   -- 공통 seller
  15000, 3000,
  NOW() + INTERVAL '7 days',
  NOW() - INTERVAL '1 minute',
  100, 1, 1,
  'test',
  'AWARDING',
  ...
FROM (SELECT id, catalog_id FROM demand_board 
       WHERE id BETWEEN 900000001 AND 900003000) db,
     generate_series(1, 4) p
ON CONFLICT DO NOTHING;
```

### 리셋 스크립트 (재실행용)

```bash
#!/bin/bash
# reset-awarding.sh — Stage 8 재실행 사이에 상태 복원

psql -c "
  UPDATE demand_board SET status='GB_AWARDING', judged_at=NULL 
    WHERE id BETWEEN 900000001 AND 900003000;
  UPDATE demand SET status='ASSIGNED' 
    WHERE demand_board_id BETWEEN 900000001 AND 900003000;
  UPDATE product SET status='AWARDING', awarded_at=NULL, closed_at=NULL
    WHERE demand_board_id BETWEEN 900000001 AND 900003000;
  DELETE FROM product_award_evaluation 
    WHERE demand_board_id BETWEEN 900000001 AND 900003000;
  DELETE FROM group_buy 
    WHERE product_id IN (SELECT id FROM product 
      WHERE demand_board_id BETWEEN 900000001 AND 900003000);
"

redis-cli DEL group_buy_outbox payment_outbox
```

### 테스트 위치: **내부 전용**

- `/api/awarding/**` 는 `InternalApiKeyFilter` 로 보호되는 내부 API. 애초에 Cloudflare 외부로 노출되지 않음 (또는 노출되면 안 됨).
- **환경**: staging 클러스터의 K8s 내부 또는 동일 VPC. AI 서비스가 실제 호출하는 경로와 동일한 네트워크 위치에서 k6 실행.
- **Internal API key**: k6 env 변수로 주입 (`k6 run stage8.js --env AWARDING_KEY=xxx`).

---

## k6 스크립트 뼈대

### 공통

```javascript
// common/config.js
export const BASE = __ENV.BASE_URL || 'http://staging-backend:8080';
export const AWARDING_KEY = __ENV.AWARDING_KEY;
```

### 시나리오 [4]

```javascript
// stage4_demand_join.js
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE } from './common/config.js';

const boards = new SharedArray('boards', () => 
  JSON.parse(open('./data/boards.json')));  // [{id, catalogId}]
const sessions = new SharedArray('sessions', () => 
  JSON.parse(open('./data/sessions.json')));  // [{cookie, memberId, payMethodId}]

export const options = {
  scenarios: {
    join_peak: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      stages: [
        { target: 200, duration: '30s' },   // ramp up
        { target: 200, duration: '3m' },    // sustain peak
        { target: 0,   duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],  // TODO: 실제 SLO로 대체
    http_req_failed: ['rate<0.001'],
  },
};

export default function () {
  const s = sessions[__VU % sessions.length];
  const b = boards[Math.floor(Math.random() * boards.length)];
  
  const payload = JSON.stringify({
    payMethodId: s.payMethodId,
    quantity: 1,
    isSubstitutable: false,
    extraRequirement: null,
    autoPaymentAgreed: true,
    privacyCollectionAgreed: true,
    privacyThirdPartyAgreed: true,
    paymentAgencyTermsAgreed: true,
  });
  
  const res = http.post(
    `${BASE}/api/demand-boards/${b.id}/join`,
    payload,
    {
      headers: { 
        'Content-Type': 'application/json', 
        'Cookie': `SID=${s.cookie}` 
      },
      tags: { name: 'join' },
    }
  );
  
  check(res, {
    'is 200': (r) => r.status === 200,
    'not 409': (r) => r.status !== 409,
  });
}
```

### 시나리오 [8]

```javascript
// stage8_awarding_result.js
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE, AWARDING_KEY } from './common/config.js';

const boardData = new SharedArray('boards', () => 
  JSON.parse(open('./data/awarding_boards.json')));
  // [{boardId, productIds: [id1, id2, ...]}]

export const options = {
  scenarios: {
    result_burst: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      stages: [
        { target: 50, duration: '30s' },
        { target: 50, duration: '2m' },
        { target: 0,  duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.001'],
  },
};

function buildPayload(boards) {
  return JSON.stringify({
    schemaVersion: 'awarding-result.v0.1',
    plannedAt: new Date().toISOString(),
    ruleVersion: 'load-test-v1',
    results: boards.map(b => ({
      boardId: b.boardId,
      judgedAt: new Date().toISOString().slice(0, 19),
      evaluations: b.productIds.map((pid, i) => ({
        productId: pid,
        score: i === 0 ? 0.87 : 0.42,
        reason: 'load test',
        isAwarded: i === 0,
      })),
    })),
  });
}

export default function () {
  // 요청당 1~5개 board 담기 (실제 AI batch 크기 유사)
  const batchSize = 1 + Math.floor(Math.random() * 5);
  const start = (__ITER * batchSize) % boardData.length;
  const batch = boardData.slice(start, start + batchSize);
  
  const res = http.post(
    `${BASE}/api/awarding/internal/result`,
    buildPayload(batch),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Internal-Api-Key': AWARDING_KEY,
      },
      tags: { name: 'awarding_result' },
    }
  );
  
  check(res, {
    'is 200': (r) => r.status === 200,
  });
}
```

---

## 실행 순서 (Makefile 예시)

```makefile
.PHONY: seed-stage4 test-stage4 reset-stage4 \
        seed-stage8 test-stage8 reset-stage8

seed-stage4:
	psql -f sql/seed-stage4.sql

test-stage4: seed-stage4
	k6 run stage4_demand_join.js \
	  --env BASE_URL=$(BASE_URL) \
	  --out json=results/stage4-$$(date +%Y%m%d-%H%M%S).json

reset-stage4:
	psql -f sql/reset-stage4.sql

seed-stage8:
	psql -f sql/seed-stage8.sql

test-stage8: seed-stage8
	k6 run stage8_awarding_result.js \
	  --env BASE_URL=$(BASE_URL) \
	  --env AWARDING_KEY=$(AWARDING_KEY) \
	  --out json=results/stage8-$$(date +%Y%m%d-%H%M%S).json

reset-stage8:
	./scripts/reset-awarding.sh
```

---

## 체크리스트 (테스트 시작 전)

- [ ] Staging 클러스터가 프로덕션과 동일 스펙인가 (CPU/Memory/DB 사양)
- [ ] 프로덕션 DAU 지표를 기반으로 peak RPS 산정 완료
- [ ] SLO 값 확정 (p95, 에러율 목표)
- [ ] Grafana 대시보드에 아래 지표 노출됨
    - [ ] HTTP p50/p95/p99
    - [ ] HTTP 에러율
    - [ ] HikariCP 커넥션 풀 활용률
    - [ ] JVM GC pause
    - [ ] `pg_stat_activity` 대기 이벤트
    - [ ] Redis `LLEN` (outbox 큐)
- [ ] 시딩 데이터 ID 범위가 프로덕션 데이터와 충돌하지 않는가
- [ ] 리셋 스크립트가 실 데이터에 영향 주지 않는가 (WHERE 조건 확인)
- [ ] 테스트 후 롤백/정리 절차 문서화

---

## 참고 자료

- [k6 Documentation](https://k6.io/docs/)
- 관련 코드
    - `DemandBoardController.join` — `src/main/java/.../DemandBoardController.java:41`
    - `DemandBoardService.join` — `src/main/java/.../DemandBoardService.java:173`
    - `AwardingController.applyAwardingResult` — `src/main/java/.../AwardingController.java:39`
    - `DemandBoardService.award` — `src/main/java/.../DemandBoardService.java:476`
    - `InternalApiKeyFilter` — `src/main/java/.../InternalApiKeyFilter.java`
