# AI 낙찰(Awarding) 플로우

`demand_board`의 모집 기간(`sale_end_at`)이 종료된 시점에, AI가 등록된 상품(`product`) 중 낙찰자를 판정하는 플로우.

이 문서는 **AI 팀, PM**과의 협의를 위한 설계 정리본이다. 백엔드 내부 배치 상세는 [
`demand-expire-batch.md`](../domain/demand-expire-batch.md) 참고.

---

## 배경

- `demand_board`는 사용자들의 수요(`demand`)를 모으는 공동구매 보드
- 판매자(`seller`)는 각자의 `product`로 입찰(`BIDDING`)
- 모집 기간(`sale_end_at`)이 끝나면 낙찰자를 정해야 함
- **낙찰 판정은 AI가 담당** (가격·수량·조건 등 종합 판단)

## 핵심 설계 원칙: 상태를 유일한 계약으로

Backend는 AI에게 **알림을 보내지 않는다.** 대신 DB 상태를 유일한 계약(contract)으로 삼는다.

- Backend: 처리해야 할 board를 `GB_AWARDING` 상태로 원자적 전이만 수행
- AI: 주기적으로 `GB_AWARDING` 상태의 board를 폴링하여 감지
- Backend와 AI 사이 **네트워크 결합이 없음** → 배포/장애로부터 서로 격리

### Push(HTTP 알림)를 쓰지 않는 이유

| 관점   | 판단                                              |
|------|-------------------------------------------------|
| 실시간성 | 배치가 매시간 도는 도메인 → 초 단위 지연 무의미                    |
| 신뢰성  | 알림 유실 시 폴링이 자동으로 보완 → Push는 최적화용에 불과            |
| 복잡도  | HTTP client, afterCommit 훅, 재시도, 관측 — 얻는 것 대비 큼 |
| 격리성  | Push는 Backend가 AI 주소를 알아야 함 → 결합 발생             |

폴링 주기(예: 1분)만큼의 지연은 이 도메인에서 수용 가능.

## 전체 흐름

```
┌─────────────────────────────────────────────────────────────┐
│  Backend Batch (매시 5분)                                    │
│                                                              │
│  1. sale_end_at 지난 GB_GATHERING board 조회                 │
│  2. board별로 BIDDING product 존재 여부 확인                  │
│     ├─ 없음 → GB_CANCELED, demand → FAILED                   │
│     └─ 있음 → GB_AWARDING, product → AWARDING (원자적)       │
│  3. 끝. (AI에게 알리지 않음)                                  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼ (AI가 자체 주기로 폴링)
┌─────────────────────────────────────────────────────────────┐
│  AI Service                                                  │
│                                                              │
│  1. 주기적 조회                     │
│  2. GB_AWARDING 상태 board 감지                              │
│  3. 낙찰자 계산                                              │
│  4. POST /api/awarding/result 로 결과 전송                    │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Backend API (AI가 호출)                                     │
│                                                              │
│  단일 트랜잭션으로 원자적 업데이트:                            │
│    - 낙찰 product → AWARDED                                  │
│    - 나머지 product → LOST                                   │
│    - demand → ASSIGNED (낙찰 seller와 연결)                  │
│    - demand_board → GB_CLOSED                                │
└─────────────────────────────────────────────────────────────┘
```

## 상태 정의

### DemandBoardStatus

| 상태                     | 의미                   | 다음 상태                        |
|------------------------|----------------------|------------------------------|
| `GB_GATHERING`         | 수요 모집 중              | `GB_AWARDING`, `GB_CANCELED` |
| **`GB_AWARDING`** (신규) | AI 낙찰 계산 대기 중        | `GB_CLOSED`, `GB_CANCELED`   |
| `GB_ACTION_REQUIRED`   | 사람 개입 필요 (환불/이의제기 등) | -                            |
| `GB_CLOSED`            | 낙찰 완료                | -                            |
| `GB_CANCELED`          | 취소됨                  | -                            |

### ProductStatus

| 상태                  | 의미          | 다음 상태                        |
|---------------------|-------------|------------------------------|
| `BIDDING`           | 입찰 중        | `AWARDING`, (board 취소 시 그대로) |
| **`AWARDING`** (신규) | AI 낙찰 계산 대상 | `AWARDED`, `LOST`            |
| `AWARDED`           | 낙찰됨         | `ON_SALE`                    |
| **`LOST`** (신규)     | 낙찰 실패       | -                            |
| `ON_SALE`           | 판매 중        | `SOLD_OUT`                   |
| `SOLD_OUT`          | 매진          | -                            |

### DemandStatus (참고)

낙찰 시 `PAYMENT_PENDING`로 전이. 이후 사용자 결제 확인 등은 별도 플로우.

## Backend / AI 책임 분담

### Backend가 하는 일

1. **모집 종료 감지** — 매시 5분마다 `sale_end_at`이 지난 `GB_GATHERING` board 조회
2. **낙찰 후보 유무 판단**
    - `BIDDING` product 있음 → `GB_AWARDING` + product를 `AWARDING`으로 전이
    - `BIDDING` product 없음 → `GB_CANCELED` + 관련 `demand`를 `FAILED`로 전이
3. **낙찰 대상 조회 API 제공** — `GET /api/awarding/pending`
4. **낙찰 결과 API 제공** — `POST /api/awarding/result` (원자적 업데이트)
5. **스턱 감지 배치** — `GB_AWARDING` 상태 30분 초과 board 감지 (선택)

### AI가 하는 일

1. **폴링** — 주기적으로 `GET /api/awarding/pending` 호출 (권장 주기: 1분)
2. **낙찰자 결정** — 응답의 product 정보 기반으로 계산
3. **결과 전송** — `POST /api/awarding/result`

## API 계약

### AI → Backend (낙찰 결과 전송)

**목적**: AI가 결정한 낙찰 결과를 Backend가 원자적으로 반영.

```http
POST /api/awarding/result
Content-Type: application/json

{
  "boardId": 12345,
  "winnerProductId": 501,
  "reason": "가격/수량 조건 최적"
}
```

**응답**:

- `200 OK` — 정상 처리
- `409 Conflict` — 이미 처리된 board (idempotency)
- `404 Not Found` — 존재하지 않거나 `GB_AWARDING` 상태가 아님

**낙찰자 없음 (모두 조건 미달)**:

```json
{
  "boardId": 12345,
  "winnerProductId": null,
  "reason": "최소 참여 수량 미달"
}
```

→ Backend가 `GB_CANCELED`로 전환.

## 타임아웃 및 복구

### 스턱 감지

AI 장애·유실 등으로 `GB_AWARDING` 상태가 오래 지속되는 board 감지.

**감지 기준**: `calculation_started_at` 컬럼 신설

```
GB_AWARDING 상태 AND (now - calculation_started_at) > 30분
```

**감지 시 액션 (미결)**:

- Option A: 알림만 발송, 운영자 개입
- Option B: 자동으로 `GB_CANCELED` 처리

### 중복 처리 방어

- **Backend 배치**: `GB_GATHERING`만 조회 → 이미 `GB_AWARDING`인 것은 재처리 안 됨
- **AI 폴링**: `GB_AWARDING` 전체를 조회하므로 이미 처리 중인 board도 재수신 → AI 측 dedup 필요
- **결과 전송**: Backend가 `409 Conflict` 반환으로 idempotency 보장
