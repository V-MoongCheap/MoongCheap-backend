# Status 값 정리

> **Java enum**: 코드에 `enum` 클래스가 존재합니다.
> **DB only**: DDL CHECK 제약으로만 정의되어 있으며 아직 Java enum 클래스가 없습니다.

## SellerStatus

> **Java enum** `com.moongcheap_backend.member.domain.SellerStatus`
> 판매자 계정의 심사·운영 상태를 나타냅니다.

| 값          | 설명                                    |
|-------------|-----------------------------------------|
| `PENDING`   | 판매자 등록 신청 후 관리자 심사 대기 중 |
| `APPROVED`  | 심사 완료, 정상 영업 가능 상태          |
| `BLOCKED`   | 관리자에 의해 판매 차단된 상태          |
| `WITHDRAWN` | 판매자 스스로 탈퇴한 상태               |

**상태 전이**

```
PENDING → APPROVED   (관리자 승인)
APPROVED → BLOCKED   (관리자 차단)
APPROVED → WITHDRAWN (판매자 탈퇴 → softDelete())
```

---

## ProductCatalogStatus

> **Java enum** `com.moongcheap_backend.product.domain.productCatalog.ProductCatalogStatus`
> `product_catalog.status VARCHAR(20)`
> 상품 도감의 노출·수요 접수 가능 여부를 나타냅니다.

| 값         | 설명                                 |
|------------|--------------------------------------|
| `ACTIVE`   | 도감 노출 및 수요 접수 가능 (기본값) |
| `INACTIVE` | 노출 중단                            |

---

## DemandStatus

> **DB only** `demand.status VARCHAR(20)`
> 구매자 수요 요청 1건의 생애주기를 나타냅니다.

| 값                   | 진행 중 여부 | 설명                                                           |
|----------------------|:------------:|----------------------------------------------------------------|
| `UNASSIGNED`         |      O       | 미배정 — 클러스터 편입 대기 (최대 2일)                         |
| `SUBSTITUTE_OFFERED` |      O       | 대체상품 제안 — 유사 클러스터 편입 제안에 대한 응답 대기       |
| `ASSIGNED`           |      O       | 배정 완료 — 수요보드 편입, 진행 중                             |
| `PAYMENT_PENDING`    |      O       | 낙찰 확정 — 본인 48시간 결제 대기                              |
| `CLOSED`             |              | 종료 — 본인 결제 완료로 요청 종결 (공구 전체 성립 여부와 무관) |
| `FAILED`             |              | 성사 실패 — 소속 보드 미낙찰 또는 최소 수량 미달, 시스템 처리  |
| `CANCELED`           |              | 사용자 취소                                                    |
| `EXPIRED`            |              | 소멸 — 미배정 2일 경과, 클러스터 생성 실패, 제안 무응답        |
| `DELETED`            |              | 운영자·시스템 무효화                                           |

> `UNASSIGNED`, `SUBSTITUTE_OFFERED`, `ASSIGNED`, `PAYMENT_PENDING` 4종을 "진행 중"으로 간주하며, 이 상태의 회원·도감
> 조합에 중복 접수 UNIQUE 제약이 적용됩니다.

**상태 전이**

```
UNASSIGNED → SUBSTITUTE_OFFERED          (대체상품 제안 수신)
UNASSIGNED → ASSIGNED                    (클러스터 편입)
UNASSIGNED → EXPIRED                     (2일 초과 미배정)
SUBSTITUTE_OFFERED → ASSIGNED            (제안 수락)
SUBSTITUTE_OFFERED → EXPIRED             (제안 무응답·거절)
ASSIGNED → PAYMENT_PENDING              (낙찰 확정)
ASSIGNED → FAILED                       (소속 보드 미낙찰 / 최소 수량 미달, 시스템)
PAYMENT_PENDING → CLOSED                (본인 결제 완료)
ASSIGNED / UNASSIGNED → CANCELED        (사용자 취소, MVP 확정 대기)
```

---

## ProductStatus

> **DB only** `product.status VARCHAR(20)`
> 판매자 응찰의 상태를 나타냅니다. 판매자는 한 수요보드에 유효 상태 응찰을 1건만 보유할 수 있습니다.

| 값         | 설명                              |
|------------|-----------------------------------|
| `BIDDING`  | 응찰 — 낙찰 판정 대기 (기본값)    |
| `AWARDED`  | 낙찰 — 해당 보드의 최종 낙찰 응찰 |
| `ON_SALE`  | 판매 중 — 여분 즉시판매           |
| `SOLD_OUT` | 품절                              |

**상태 전이**

```
BIDDING → AWARDED   (낙찰 판정)
AWARDED → ON_SALE   (여분 판매 개시)
ON_SALE → SOLD_OUT  (재고 소진)
```

---

## DemandBoardStatus

> **DB only** `demand_board.status VARCHAR(20)`
> 수요 클러스터 (공구)의 진행 상태를 나타냅니다.
>

| 값 (기능정의서 기준) | 설명                                                  |
|----------------------|-------------------------------------------------------|
| `GB_GATHERING`       | 모이는 중 — 응찰 접수 및 참여자 모집                  |
| `GB_ACTION_REQUIRED` | 확인 필요 — 낙찰 확정, 참여자별 48시간 결제 확인 대기 |
| `GB_CLOSED`          | 종료 — 전원 결제 완료, 주문 생성                      |
| `GB_CANCELED`        | 취소 — MVP에서는 도달하지 않음                        |

---

## GroupBuyStatus

> **Java enum** `com.moongcheap_backend.groupbuy.domain.GroupBuyStatus`
> `group_buy.status VARCHAR(30)`
> 공동 구매의 진행 상태를 나타냅니다.
>

| 값 (기능정의서 기준)    | 설명                                                            |
|-------------------------|-----------------------------------------------------------------|
| `OPEN`                  | 공동구매 오픈                                                   |
| `RECRUITMENT_COMPLETED` | 공동구매 성사 - 인원등의 요건 충족                              |
| `CLOSED`                | 공동구매 종료 - 공동구매가 종료됬을 때. 전부 구매확정 뭐 그럴때 |
| `FAILED`                | 공동구매 성사 실패 - 요건 충족 실패                             |
| `CANCELED`              | 공동구매 취소 - 판매자, 관리자 전용                             |

**상태 전이**

```
OPEN → RECRUITMENT_COMPLETED   (공동구매 성사 판정 통과)
RECRUITMENT_COMPLETED → CLOSED   (공동구매의 종료)
OPEN → FAILED   (판정 통과 실패)
OPEN, RECRUITMENT_COMPLETED → CANCELED   (판매자, 관리자에 의한 공동구매의 취소)
```

---

## OutboxEventStatus

> **Java enum** `com.moongcheap_backend.common.outbox.domain.OutboxEventStatus`
> `outbox_event.status VARCHAR(20)`
> DB에 저장된 이벤트가 Redis Sorted Set에 등록됐는지를 나타냅니다.

| 값          | 설명                                            |
|-------------|-------------------------------------------------|
| `PENDING`   | Redis 발행 대기 또는 발행 실패 후 재시도 대기  |
| `PUBLISHED` | Redis Sorted Set 등록 완료                      |

**상태 전이**

```
PENDING → PUBLISHED   (Redis Sorted Set 등록 성공)
PENDING → PENDING     (등록 실패 후 지수 백오프로 재시도)
```

---

## OrderStatus

> **Java enum** `com.moongcheap_backend.order.domain.OrderStatus`
> `orders.order_status VARCHAR(30)`
> 주문의 진행 상태를 나타냅니다.
>

| 값 (기능정의서 기준) | 설명                              |
|----------------------|-----------------------------------|
| `PAYMENT_PENDING`    | 결제 대기                         |
| `PAYMENT_COMPLETED`  | 결제 완료                         |
| `PAYMENT_FAILED`     | 결제 실패                         |
| `PREPARING_SHIPMENT` | 상품 준비 중                      |
| `SHIPPED`            | 배송 중                           |
| `DELIVERED`          | 배송 완료                         |
| `COMPLEDED`          | 구매확정                          |
| `CANCELED`           | 주문 취소 - 배송 이전에 가능      |
| `REFUND_PENDING`     | 환불 처리 중 - 배송중 이후로 가능 |
| `REFUNDED`           | 환불 완료                         |

**상태 전이**

```
PAYMENT_PENDING → PAYMENT_COMPLETED   (결제 완료)
PAYMENT_PENDING → PAYMENT_FAILED   (결제 실패)
PAYMENT_COMPLETED → PREPARING_SHIPMENT   (배송지 입력)
PREPARING_SHIPMENT → SHIPPED   (배송 시작)
SHIPPED → DELIVERED   (배송 완료)
DELIVERED → COMPLEDED   (사용자 구매확정, 시스템 구매확정)
PAYMENT_PENDING, PAYMENT_COMPLETED, PAYMENT_FAILED, PREPARING_SHIPMENT → CANCELED   (주문 취소)
SHIPPED, DELIVERED, COMPLEDED → REFUND_PENDING   (환불 신청)
REFUND_PENDING → REFUNDED   (환불 완료)
```

---

## PaymentsStatus

> **Java enum** `com.moongcheap_backend.payments.domain.enums.PaymentsStatus`
> `payments.payments_status VARCHAR(30) NOT NULL DEFAULT 'READY'`
> 결제 생성부터 승인·취소·실패·만료까지의 처리 상태를 나타냅니다.

| 값            | 설명                                        |
|---------------|---------------------------------------------|
| `READY`       | 결제 생성 후 결제수단 인증 전 상태 (기본값) |
| `IN_PROGRESS` | 결제수단 인증 완료, 결제 승인 대기 상태     |
| `DONE`        | 결제 승인 완료                              |
| `CANCELED`    | 승인된 결제의 취소 완료                     |
| `ABORTED`     | 결제 승인 실패                              |
| `EXPIRED`     | 결제 유효 시간 만료                         |

**상태 전이**

```
READY → IN_PROGRESS   (결제수단 인증 완료)
READY → EXPIRED       (인증 전 결제 유효 시간 만료)
IN_PROGRESS → DONE    (결제 승인 완료)
IN_PROGRESS → ABORTED (결제 승인 실패)
IN_PROGRESS → EXPIRED (승인 대기 중 결제 유효 시간 만료)
DONE → CANCELED       (결제 취소 완료)
```

---

## PaymentsMethodStatus

> **Java enum** `com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus`
> `brand_pay_method.status VARCHAR(30) NOT NULL`
> 회원이 등록한 브랜드페이 결제수단의 사용 상태를 나타냅니다.

| 값         | 설명                               |
|------------|------------------------------------|
| `ACTIVE`   | 결제수단 사용 가능                 |
| `INACTIVE` | 결제수단 사용 불가                 |
| `EXPIRED`  | 삭제 또는 만료로 더 이상 사용 불가 |

**상태 전이**

```
ACTIVE → INACTIVE          (결제수단 비활성화)
ACTIVE, INACTIVE → EXPIRED (결제수단 삭제 또는 만료)
```
