<div align="center">

# 🧭 주요 처리 흐름 다이어그램

### "수요 등록 → 공동구매 편성 → 낙찰 → 주문 자동 생성 → 자동 결제"

**MoongCheap 백엔드의 전 과정 시퀀스**

</div>

<br>

## 📌 Intro

MoongCheap 백엔드가 수요 접수부터 자동결제까지를 어떻게 이어 붙이는지를 **4개의 시퀀스 다이어그램**으로 정리합니다.

- 각 다이어그램은 [`docs/images/`](images/) 하위 `.mmd` 원본에서 발췌한 것입니다.
- 번호(`autonumber`)는 전체 시퀀스(1~30+)에서의 위치를 뜻합니다. 세부 흐름 다이어그램의 번호는 **전체 시퀀스에서의 연속성**을 유지합니다.
- 상세 설계·장애 처리 근거는 각 다이어그램 아래 "관련 문서"에서 확인할 수 있습니다.

<br>

## 1️⃣ 전체 흐름 한눈에

수요자가 수요를 올린 뒤 공동구매가 결제까지 성사되는 전 과정을 한 장으로 압축한 다이어그램입니다.

<div align="center">

![수요에서 낙찰까지 전체 흐름](images/5-2-demand-to-awarding-flow.svg)

</div>

```mermaid
sequenceDiagram
autonumber
actor U as 수요자(User)
actor OP as 운영자/관리자
participant B as Backend
participant DB as DB (Demand/Board/Product)
participant R as Redis (Outbox 큐)
participant SCH as @Scheduled
participant AI as AI Service
participant BP as Toss BrandPay

Note over U,B: [1] 수요 생성
U->>B: POST /api/members/me/demand
B->>DB: INSERT demand (status=UNASSIGNED)
B-->>U: 200 { demandId }

Note over AI,DB: [2] AI가 DB에서 직접 SELECT (Backend endpoint 없음)
AI->>DB: SELECT demand WHERE status=UNASSIGNED

Note over AI,B: [3] AI 수요 취합 → DemandBoard 편성
AI->>B: POST /api/demand-boards/internal/formation-plans
activate B
B->>DB: INSERT demand_board (status=GB_GATHERING)
B->>DB: UPDATE demand SET status=ASSIGNED,<br/>demand_board_id=?
B-->>AI: 200 FormationPlanResponse
deactivate B

Note over U,B: [4] 수요 참여 — 기존 보드에 신규 참여자
U->>B: POST /api/demand-boards/{id}/join
B->>DB: SELECT demand_board FOR UPDATE (락)
B->>DB: INSERT demand (status=ASSIGNED)<br/>+ UPDATE participant_count
B-->>U: 200 { demandId }

Note over OP,DB: [5] 상품 및 카탈로그 등록 (DB 직접 INSERT)
OP->>DB: INSERT product_catalog
OP->>DB: INSERT product (status=ACTIVE / AWARDING)

Note over SCH,B: [6] 판정 준비 — sale_end_at 도래
SCH->>B: tick (GroupBuyJudgmentScheduler)
B->>DB: UPDATE demand_board<br/>SET status=GB_ACTION_REQUIRED → GB_AWARDING

Note over AI,B: [7] AI 폴링 — 판정 대기 보드 조회
loop 1분 주기
  AI->>B: GET /api/awarding/internal/pending?size=50
  B->>DB: SELECT demand_board WHERE status=GB_AWARDING
  B-->>AI: [board list + 참여 정보]
end

Note over AI,B: [8] AI 낙찰 결과 반영
AI->>B: POST /api/awarding/internal/result
activate B
B->>DB: UPDATE demand_board GB_AWARDING → GB_ACTION_REQUIRED (or GB_CANCELED)
B->>DB: UPDATE demand ASSIGNED → PAYMENT_PENDING (or FAILED)
B->>DB: UPDATE product AWARDING → AWARDED/LOST
B->>DB: INSERT product_award_evaluation
B->>DB: INSERT group_buy (winner 있는 경우)
B->>R: LPUSH group_buy_outbox (createOrder 지시)
B-->>AI: 200 AwardingResultResponse
deactivate B

Note over R,B: [9] 주문 자동 생성 (Redis 소비)
SCH->>B: GroupBuyOrderCreationConsumer tick
B->>R: BRPOP group_buy_outbox
activate B
B->>DB: SELECT demand (status=PAYMENT_PENDING)
loop chunk (ORDER_BATCH_SIZE 씩)
  B->>DB: INSERT orders (batch)
end
B->>R: LPUSH payment_outbox (자동결제 지시)
deactivate B

Note over R,BP: [10] 자동 결제 (BrandPay)
SCH->>B: PaymentWorkerPool tick
B->>R: BRPOP payment_outbox
activate B
B->>BP: POST /v1/brandpay/authorizations/...
BP-->>B: accessToken
B->>BP: POST /v1/brandpay/payments (자동결제)
BP-->>B: 결제 결과
B->>DB: UPDATE payment / orders (SUCCEEDED / FAILED)
B->>DB: UPDATE demand (PAYMENT_PENDING → PAID / FAILED)
deactivate B
```

> **관련 문서** · [시스템 아키텍처](architecture/system-architecture.md) · [AI 낙찰 플로우](domain/ai-awarding-flow.md)

<br>

## 2️⃣ 수요 생성과 보드 편성 (수요자 ↔ AI)

수요자가 수요를 등록하면 AI가 DB를 직접 조회해 보드를 편성하고, 이후 신규 참여자가 같은 보드에 합류하는 흐름입니다.

```mermaid
sequenceDiagram
autonumber 1
actor U as 수요자(User)
actor OP as 운영자/관리자
participant B as Backend
participant DB as DB
participant AI as AI Service

Note over U,B: [1] 수요 생성
U->>B: POST /api/members/me/demand
B->>DB: INSERT demand (status=UNASSIGNED)
B-->>U: 200 { demandId }

Note over DB,AI: [2] AI가 DB에서 직접 SELECT (Backend endpoint 없음)
AI->>DB: SELECT demand WHERE status=UNASSIGNED

Note over B,AI: [3] AI 수요 취합 → DemandBoard 편성
AI->>B: POST /api/demand-boards/internal/formation-plans
activate B
B->>DB: INSERT demand_board (status=GB_GATHERING)
B->>DB: UPDATE demand SET status=ASSIGNED,<br/>demand_board_id=?
B-->>AI: 200 FormationPlanResponse
deactivate B

Note over U,B: [4] 수요 참여 — 기존 보드에 신규 참여자
U->>B: POST /api/demand-boards/{id}/join
B->>DB: SELECT demand_board FOR UPDATE (락)
B->>DB: INSERT demand (status=ASSIGNED)<br/>+ UPDATE participant_count
B-->>U: 200 { demandId }

Note over OP,DB: [5] 상품 및 카탈로그 등록 (DB 직접 INSERT)
OP->>DB: INSERT product_catalog
OP->>DB: INSERT product (status=ACTIVE / AWARDING)
```

> **관련 문서** · [수요 쿼리/인덱스 설계](domain/demand-board-query-index.md) · [수요 만료 배치](domain/demand-expire-batch.md)

<br>

## 3️⃣ 낙찰 평가 (스케줄러 → AI → Outbox)

마감 시각에 보드 상태를 `GB_AWARDING`으로 전환한 뒤, AI가 결과를 전달하면 백엔드가 상태를 반영하고 **주문 생성 이벤트를 Redis 큐에 올립니다**.

```mermaid
sequenceDiagram
autonumber 15
participant SCH as @Scheduled
participant AI as AI Service
participant B as Backend
participant DB as DB
participant R as Redis (Outbox 큐)

Note over SCH,B: [6] 판정 준비 — sale_end_at 도래
SCH->>B: tick (GroupBuyJudgmentScheduler)
B->>DB: UPDATE demand_board<br/>SET status=GB_ACTION_REQUIRED → GB_AWARDING

Note over AI,B: [7] AI 폴링 — 판정 대기 보드 조회
loop 1분 주기
  AI->>B: GET /api/awarding/internal/pending?size=50
  B->>DB: SELECT demand_board WHERE status=GB_AWARDING
  B-->>AI: [board list + 참여 정보]
end

Note over AI,B: [8] AI 낙찰 결과 반영
AI->>B: POST /api/awarding/internal/result
activate B
B->>DB: UPDATE demand_board GB_AWARDING → GB_ACTION_REQUIRED (or GB_CANCELED)
B->>DB: UPDATE demand ASSIGNED → PAYMENT_PENDING (or FAILED)
B->>DB: UPDATE product AWARDING → AWARDED/LOST
B->>DB: INSERT product_award_evaluation
B->>DB: INSERT group_buy (winner 있는 경우)
B->>R: LPUSH group_buy_outbox (createOrder 지시)
B-->>AI: 200 AwardingResultResponse
deactivate B
```

> **관련 문서** · [마감·공동구매 판정 버그 리포트](troubleshooting/deadline-and-judgment-bug-report.md) · [AI 낙찰 플로우](domain/ai-awarding-flow.md)

<br>

## 4️⃣ 주문 자동 생성과 자동 결제 (Redis Consumer → Toss)

Outbox가 Redis 큐로 전달되면 전용 소비자가 주문 생성과 결제 호출을 담당합니다. **외부 I/O는 모두 트랜잭션 밖에서** 수행합니다.

```mermaid
sequenceDiagram
autonumber 28
participant SCH as @Scheduled
participant B as Backend
participant R as Redis (Outbox 큐)
participant DB as DB
participant BP as Toss BrandPay

Note over SCH,DB: [9] 주문 자동 생성 (Redis 소비)
SCH->>B: GroupBuyOrderCreationConsumer tick
B->>R: BRPOP group_buy_outbox
activate B
B->>DB: SELECT demand (status=PAYMENT_PENDING)
loop chunk (ORDER_BATCH_SIZE 씩)
  B->>DB: INSERT orders (batch)
end
B->>R: LPUSH payment_outbox (자동결제 지시)
deactivate B

Note over SCH,BP: [10] 자동 결제 (BrandPay)
SCH->>B: PaymentWorkerPool tick
B->>R: BRPOP payment_outbox
activate B
B->>BP: POST /v1/brandpay/authorizations/...
BP-->>B: accessToken
B->>BP: POST /v1/brandpay/payments (자동결제)
BP-->>B: 결제 결과
B->>DB: UPDATE payment / orders (SUCCEEDED / FAILED)
B->>DB: UPDATE demand (PAYMENT_PENDING → PAID / FAILED)
deactivate B
```

> **관련 문서** · [Redis 결제 큐 설계](brandpay/brandpay-redis-sorted-set-design.md) · [주문 배송비 수정](troubleshooting/order-delivery-fee-fix.md) · [BrandPay 프런트엔드 연동 가이드](brandpay/brandpay-frontend-integration-guide.md)

<br>

<div align="center">

**← [프로젝트 홈으로](../README.md)**

</div>
