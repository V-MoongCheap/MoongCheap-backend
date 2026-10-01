# 브랜드페이 자동결제: Redis Sorted Set 설계

작성일: 2026-09-22

상태: 구현 반영. 결제 Outbox 연계, Payments.nextAttemptAt 미사용, 기본 크기 1의 확장 가능한 워커 풀 및 실행 소유권 유지 방향을 코드와 V16 마이그레이션에 반영했다. 큐는 기본 비활성이다.

현재 코드 기준: 이전 PostgreSQL 큐 구현은 롤백한 뒤 이 문서의 Redis Sorted Set + Outbox 구조를 새 V16으로 구현했다. Payments에는 nextAttemptAt을 두지 않고 attemptCount, processingToken, processingDeadline, idempotencyKey와 고정 요청 정보를 저장한다. 별도로 과거 DB 큐 V16/V17을 적용한 환경이 있다면 현재 V16과 번호·내용이 충돌하므로 배포 전에 반드시 별도 전환 마이그레이션을 결정한다.

관련 문서: [설계 논의 기록](brandpay-design-history.md), [PostgreSQL 큐 설계](brandpay-postgresql-queue-design.md).

## 1. 선택한 구조

결제 예약과 재시도 대상 탐색에 **결제 전용 Sorted Set 하나**를 사용한다. Redis Stream, List, 처리 중 작업용 별도 Sorted Set, 결제별 Redis 락은 추가하지 않는다.

- 최초 결제와 재시도 모두 paymentId로 같은 큐에 등록한다.
- DB는 결제 사실, 재시도 정책, 실행 소유권의 기준이다. 예약 시각과 Redis 전달 상태는 공통 Outbox가 보관한다.
- 결제 변경과 Outbox 예약 변경을 같은 DB 트랜잭션에서 커밋한다.
- Redis는 지금 확인할 paymentId를 빠르게 찾는 재구성 가능한 예약 목록이다.
- 워커는 Redis에서 후보를 받고 DB에서 해당 paymentId의 실행 가능 여부를 검증한다.
- 결제 전용 워커 풀 크기는 기본 1이다. 현재는 순차 처리하지만 설정으로 워커 수를 늘릴 수 있도록 설계한다.
- 스케줄러는 가용 슬롯에 실행 작업만 제출한다. 후보 조회·DB 선점·토스 호출은 워커가 담당한다.
- Redis 후보 선점과 DB 실행 선점은 다르다. 실제 청구 권한은 DB에서만 확정한다.
- 결제 취소는 공동구매 판정 전까지만 허용한다는 기존 정책을 유지한다.

```text
성사한 주문 → [Payments + Outbox 예약] 같은 DB 트랜잭션 커밋
                              ↓
                    결제 Outbox Publisher
                              ↓
                     Redis Sorted Set 등록
                              ↓
                    가용 워커 → DB 실행 선점
                              ↓
                      토스 조회 또는 청구
                              ↓
             [DB 결과 + Outbox 변경] 같은 트랜잭션 커밋
                              ↓
                  Publisher가 Redis 갱신·삭제
```

이 안은 DB 안전장치를 Redis로 전부 옮기는 안이 아니다. DB의 전체 실행 대상 폴링을 Redis 후보 탐색으로 바꾸는 혼합 구조다. DB와 Redis의 동기화가 추가되므로 기존 DB 큐보다 무조건 단순하거나 빠르다고 전제하지 않는다.

## 2. Redis 데이터

| 항목 | 값 |
| --- | --- |
| key | `moongcheap:payment:execution:scheduled` |
| 자료구조 | Sorted Set |
| member | 문자열 paymentId. 예: `1024` |
| score | 다음 후보 조회 가능 시각, Unix epoch milliseconds |
| TTL | 큐 키에는 설정하지 않음 |

score는 평소에는 실행 예약 시각이고, 워커가 후보를 가져간 동안에는 임시 재노출 시각이다. 실제 실행 시각은 최신 Outbox.scheduledAt, 임대는 Payments.processingDeadline으로 재검증한다.

결제금액, customerKey, methodKey, 멱등키, 인증 토큰, 시도 횟수는 Redis member에 넣지 않는다. paymentId를 통해 DB에서 읽는다.

기존 공동구매 판정 큐 `moongcheap:group-buy:judgment:pending`과 분리한다. 기존 `GroupBuyJudgmentSchedule`의 member/score 형태는 참고하되 결제 실행과 복구를 같은 키에 섞지 않는다.

Sorted Set은 동일 member를 중복 보관하지 않으며 `ZADD`로 score를 변경할 수 있다. 이는 큐 항목 중복을 줄일 뿐 외부 결제의 중복 실행을 보장해 주는 것은 아니다. [Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/), [ZADD](https://redis.io/docs/latest/commands/zadd/).

## 3. DB에 유지할 정보

Payments의 실행 관리 필드는 아래 네 개만 유지한다. nextAttemptAt은 제거하고 예약 시각은 Outbox.scheduledAt으로 옮긴다.

| Payments 필드 | 유지 이유 |
| --- | --- |
| attemptCount | 조회·청구를 합친 실행 횟수 제한 |
| processingToken | 늦게 끝난 이전 워커의 결과 저장 차단 |
| processingDeadline | DB 선점의 만료 및 재선점 판단 |
| idempotencyKey | 최초 청구와 재전송에 동일한 키 사용 |

기존 status, createdAt, 주문번호, 금액, 상품명, 결제수단 참조, customerKey 스냅샷도 유지한다. 별도 boolean 처리 플래그나 요청 버전 필드는 추가하지 않는다. 수동 중단은 REVIEW_REQUIRED라는 로컬 enum 값으로 구분하는 안을 사용한다.

- `PENDING`: 아직 불명확한 외부 요청이 없는 대기 상태.
- `UNKNOWN`: 요청 전 기록부터 결과 확인 전까지의 자동 복구 대상.
- `REVIEW_REQUIRED`: 승인 여부를 추정하지 않고 자동 처리를 멈춘 수동 확인 대상.
- `SUCCEEDED`, `FAILED`, `CANCELED`: 해당 청구 작업 종료.
- REVIEW_REQUIRED는 실패 확정이 아니다. 기존 미확정 결제를 새로 청구하지 못하도록 활성 주문 유니크 제약에도 포함한다.

Redis만 사용하면 정확한 예약 복원은 어렵지만, 이 개정안에서는 Outbox에 최신 예약 시각을 남긴다. 따라서 Payments.nextAttemptAt 없이도 원래 예약을 복원할 수 있다. 실행 시각의 영속 원본은 Outbox, 실행용 인덱스는 Redis다. processingToken은 결과 저장과 함께 검증해야 하므로 DB에 유지한다.

### 워커가 1개여도 실행 소유권을 유지하는 이유

풀 크기 1은 현재의 처리량 설정이지 영구적인 단일 실행자 보장이 아니다. 향후 워커 수 증가, 서버 인스턴스 추가, 배포 시 이전·신규 프로세스 중첩을 고려해 processingToken과 processingDeadline을 처음부터 유지한다. 로컬 슬롯 제한은 작업 제출 수를 제어하고, DB 토큰·데드라인은 서버를 넘는 실행 소유권과 장애 복구를 제어한다.

멱등키도 유지한다. 실행 소유권은 로컬 DB의 늦은 쓰기를 막고, 멱등키는 외부 요청 재전송을 식별하므로 서로 대체할 수 없다. 불변 paymentId로 키를 계산하는 것은 가능한 후속 선택이지만, 이번 문서에서는 기존 idempotencyKey 저장 방식을 유지한다. 이미 요청한 결제의 키는 변경하지 않는다.

## 4. 최초 예약

공동구매 판정이 성사되면 `GroupBuyJudgmentService.judgeAndPay()`의 트랜잭션이 반환되어 커밋된 뒤, `GroupBuyJudgmentScheduler`가 `GroupPaymentReservationService.scheduleForGroup(groupBuyId)`를 호출한다. 모집 실패 판정은 결제 예약을 호출하지 않는다.

1. 짧은 DB 트랜잭션에서 주문을 잠근다.
2. 공동구매 성사와 주문 상태, 결제수단, 요청 정보를 확인한다.
3. 기존 Payments가 있으면 재사용한다. 실패·UNKNOWN을 새로운 결제로 자동 대체하지 않는다.
4. 새 Payments에 고정 요청 정보, 멱등키, PENDING을 저장한다.
5. 같은 트랜잭션에서 PAYMENT_SCHEDULE_SYNC Outbox를 scheduledAt=현재 시각으로 생성한다.
6. 커밋 후 Publisher가 Outbox를 읽어 Redis에 등록한다.

Payments와 Outbox는 반드시 하나의 DB 트랜잭션에 참여한다. Outbox 기록만 REQUIRES_NEW로 분리하지 않는다. 커밋 직후 프로세스가 종료돼도 PENDING Outbox가 남으므로 Publisher가 나중에 전달한다. after-commit 직접 ZADD는 별도 실행 경로로 두지 않는다.

직접 연결 중 개별 주문 예약이 실패하거나 프로세스가 종료된 경우에는 보완 작업이 Payments가 없는 주문을 다시 찾아 예약한다. 판정 성공은 결제 예약 장애로 되돌리지 않는다. 주문당 활성·성공 결제 중복을 막는 기존 DB 제약은 유지한다.

## 5. Sorted Set 후보 확보

`ZPOPMIN`으로 먼저 삭제하지 않는다. 삭제 직후 워커가 종료되면 Redis에 복구할 흔적이 남지 않기 때문이다. 대신 실행 시각이 도래한 한 건의 score를 잠시 미래로 이동한다.

아래 개념 작업을 짧은 Lua 스크립트 하나로 수행한다.

```text
now = Redis TIME을 epoch milliseconds로 변환
candidate = score <= now인 첫 member 한 건 조회
없으면 종료
candidate의 score = now + visibilityDelay
candidate의 paymentId 반환
```

스크립트는 같은 Redis 키에 대한 후보 조회와 score 변경을 원자적으로 묶는다. Redis 스크립트 실행 중에는 다른 명령이 대기하므로 1건만 처리하고 대량 조회나 외부 작업을 넣지 않는다. [Redis Lua scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/).

기본 visibilityDelay 제안은 60초다. 이 값은 DB 실행 소유권이 아니라 일시적인 재노출 지연이다. 후보 반환 응답이 유실되거나 워커가 바로 종료돼도, score의 시각이 지나면 다시 조회된다.

여러 워커는 이 스크립트를 각각 실행한다. 가용 실행 슬롯이 생긴 워커만 후보를 가져오며, 여러 건을 미리 받아 Executor 대기열에 쌓지 않는다.

Redis 재시작, 보완 작업, 오래된 ZADD 등으로 같은 후보가 다시 노출될 수 있다. Lua만으로 정확히 한 번 실행을 보장한다고 해석하지 않는다.

## 6. DB 실행 선점과 토스 호출

후보를 받은 워커는 paymentId로 짧은 DB 트랜잭션을 시작한다. 해당 결제 행을 잠그고 다음을 확인한다. 가능하면 잠긴 행은 기다리지 않고 건너뛰는 조회를 사용한다.

1. 종료 상태 또는 수동 확인 대상이면 청구하지 않는다.
2. 유효한 processingToken/deadline이 있으면 다른 워커의 실행이므로 청구하지 않는다.
3. 기존 임대가 만료됐으면 이전 토큰을 폐기하고 재선점할 수 있다.
4. 해당 결제의 Outbox가 있는지 확인한다. 없으면 자동 청구하지 않고 REVIEW_REQUIRED로 전환한다. 최신 scheduledAt이 미래면 청구하지 않는다. Outbox 전달 상태 PENDING/PUBLISHED는 청구 결과가 아니므로 실행 권한 판단 기준으로 사용하지 않는다.
5. 횟수·생성 시각 기준 기간 제한과 요청 정보를 검사한다.
6. 새로운 UUID 토큰과 DB 현재 시각 기준 deadline을 설정한다.
7. 진입 당시 상태로 최초 청구인지 UNKNOWN 확인인지 결정한다.
8. attemptCount를 한 번 증가시키고 UNKNOWN을 저장한 뒤 커밋한다.

후보 확보만 했거나 실행 검증에서 탈락한 경우는 attemptCount를 증가시키지 않는다. 같은 실행에서 조회 후 재전송하더라도 추가 증가하지 않는다.

이후 DB 트랜잭션 밖에서 기존 토스 클라이언트를 호출한다.

- 최초 실행이면 고정 요청과 멱등키로 청구한다.
- UNKNOWN이면 기존 주문번호로 결과 조회를 우선한다.
- 미조회 응답을 미결제 확정으로 보지 않는다. 검증된 복구 조건과 기간 안에서만 동일 요청·동일 키로 재전송한다.
- 조회 후 재전송 직전에 DB 토큰·임대·기간·수단 유효성을 다시 확인한다.

Redis 후보 선점과 DB 선점을 한 트랜잭션으로 묶지 않는다. Redis는 후보 탐색, DB는 실행 권한이라는 책임 분리로 다룬다. 외부 요청과 DB를 원자적으로 묶을 수 없다는 문제도 기존과 동일하다.

## 7. 결과 반영과 Redis 정리

결제·주문 결과와 Outbox 변경을 같은 트랜잭션에 커밋한다. Redis 갱신·삭제는 Publisher가 담당한다. 워커가 결과 저장 후 직접 다음 예약을 ZADD하는 이중 경로는 두지 않는다.

| 결과 | DB 처리 | 커밋 후 Redis 처리 |
| --- | --- | --- |
| 승인 성공 | 토큰 검사, 결제·주문 완료, 선점 해제, Outbox PENDING | Publisher가 `ZREM paymentId` |
| 확정 실패 | 토큰 검사, 결제·주문 실패, 선점 해제, Outbox PENDING | Publisher가 `ZREM paymentId` |
| 불명확·재시도 가능 | UNKNOWN, 선점 해제, Outbox scheduledAt 갱신·PENDING | Publisher가 `ZADD scheduledAt paymentId` |
| 횟수·기간 한도 또는 검증 문제 | REVIEW_REQUIRED, 선점 해제, Outbox PENDING | Publisher가 `ZREM paymentId` |
| 소유권 상실 | DB 변경하지 않음 | 이전 워커의 결과로 삭제·재예약하지 않음 |

결과 저장은 주문 → 결제 순서로 잠그고 DB processingToken을 비교한다. 임대가 지났더라도 아직 교체되지 않은 토큰의 결과는 허용하되, 새 토큰으로 교체됐다면 주문 상태까지 포함해 변경을 거절한다.

Redis 갱신은 DB와 원자적이지 않다. 커밋 후 늦게 실행된 ZADD가 새 예약 시각을 덮거나, 정리 지연으로 종료 작업이 다시 보일 수 있다. Redis score를 실행 권한으로 사용하지 않으므로 이 경우도 워커는 반드시 DB를 다시 확인한다. 불일치는 보완 작업으로 교정한다.

Redis 명령 실패 때문에 이미 커밋한 성공 결제를 되돌리거나 토스에 다시 청구하지 않는다. 현재 DB 값의 Redis 반영만 다시 시도한다.

## 8. 워커 중단과 재시도

처리 중 Sorted Set을 따로 만들지 않는다. 같은 Sorted Set에 미래 score로 남긴 항목이 다시 도래하면 복구 후보가 된다.

- Redis 후보 확보 직후 종료: visibilityDelay 이후 다시 발견한다.
- DB 실행 시작 커밋 후 종료: UNKNOWN과 토큰·deadline이 남는다. 재노출 후 DB 임대가 만료됐으면 새 토큰으로 조회 복구한다.
- 재노출됐지만 DB 임대가 아직 유효함: 실행하지 않고 현재 DB deadline을 기준으로 재노출을 늦춘다.
- Redis 시계와 DB 시계가 다름: 조기 재노출은 DB 검증으로 차단한다. 시계 차이는 실행 지연을 유발할 수 있으므로 시간 동기화를 운영 전제로 둔다.
- 승인 응답 후 DB 저장 실패: UNKNOWN을 유지하고 임대 만료 후 결과를 조회한다.
- 이전 워커의 늦은 응답: DB 토큰이 다르면 무시한다. 이미 전송된 HTTP 요청은 취소되지 않으므로 동일 멱등키가 계속 필요하다.

기본 정책 제안은 통합 실행 최대 5회, 백오프 10초 → 30초 → 2분 → 10분(소량의 jitter 추가)이다. 서버 종료도 실행 시작이 커밋됐다면 1회를 소모한다. 횟수 초과를 결제 실패로 단정하지 않는다.

재전송 기간은 기존 createdAt 기준 정책을 유지한다. 이전 설계의 14일은 보수적인 설정값이며 실제 적용 시 토스 계약·멱등키 유효 범위를 재확인한다. 모든 인스턴스에서 createdAt의 시간대 해석을 일치시킨다. API 키·경로·요청 본문 변경 시 기존 불명확 건의 재전송 안전성을 별도로 확인해야 한다.

## 9. 공통 Outbox 연계

### 9.1 현재 코드와 변경 범위

현재 공통 outbox_event에는 GROUP_BUY_ORDER_CREATION_REQUESTED, GROUP_BUY_JUDGMENT_SCHEDULED만 있다. GroupBuyOutboxPublishService가 모든 PENDING 이벤트를 조회해 발행한다. 결제 연계는 아직 없다.

기존 테이블을 재사용하고 PAYMENT_SCHEDULE_SYNC 타입을 추가하는 안으로 한다. 기존 공동구매 주문 생성 Stream은 변경하지 않는다. **결제 전달 경로에는 Stream을 사용하지 않는다.**

현재 유니크 제약은 (event_type, aggregate_id)다. 따라서 결제 재시도마다 같은 타입·paymentId로 새 이벤트를 INSERT하면 충돌한다. 이 설계는 제약을 제거하는 대신 **결제당 예약 동기화 행 하나를 갱신**한다. 불변 이벤트 이력을 쌓는 방식이 아니라 최신 예약 의도를 전달하는 상태 기반 Outbox다.

### 9.2 필드 매핑

| Outbox 필드 | 결제 이벤트에서의 의미 |
| --- | --- |
| eventType | PAYMENT_SCHEDULE_SYNC |
| aggregateId | paymentId. 기존 공동구매 이벤트에서는 계속 groupBuyId |
| scheduledAt | 현재 결제의 실행 예정 시각, Redis score의 영속 원본 |
| status | PENDING=Redis 반영 필요, PUBLISHED=마지막 반영 성공 |
| retryCount | Redis 전달 실패 횟수. Payments.attemptCount와 별개 |
| nextAttemptAt | **Publisher의 다음 전달 시각**. 결제 실행 시각이 아님 |
| publishedAt | 마지막 Redis 반영 성공 시각 |

Payments.nextAttemptAt을 제거해도 Outbox.nextAttemptAt은 유지한다. 하나는 결제 실행 예약이었고 다른 하나는 전송 장애 백오프이므로 의미가 다르다. 미래 결제도 Redis에는 즉시 등록할 수 있도록 Outbox.nextAttemptAt은 보통 현재 시각으로 둔다.

결제 재예약 시에는 scheduledAt을 새 시각으로 갱신하고 status=PENDING, retryCount=0, nextAttemptAt=현재 시각, publishedAt=null로 되돌린다. 종료·수동 확인 시에는 scheduledAt을 더 이상 실행에 사용하지 않고 동일 행을 PENDING으로 만들어 삭제를 전달한다. 별도 DELETE 타입 없이 최신 Payments.status로 ZADD/ZREM을 결정한다.

기존 markPublished/scheduleRetry만으로는 재예약 전환을 표현할 수 없으므로 팩토리와 재예약·재동기화 메서드 추가가 필요하다. 공통 필드 주석도 공동구매 전용 의미에서 확장해야 한다.

### 9.3 Publisher와 잠금 순서

결제 Publisher는 다음 순서로 처리한다.

1. 발행 시각이 도래한 **결제 타입만** 후보 ID로 제한 조회한다. 이 단계에서 Outbox 행을 먼저 잠그지 않는다.
2. 이벤트마다 별도 트랜잭션에서 Payments → Outbox 순서로 잠근다. 이미 잠긴 작업은 건너뛰거나 짧은 타임아웃으로 다음 회차에 넘긴다.
3. 잠근 뒤 타입·PENDING·전달 시각을 다시 검증한다.
4. Payments가 PENDING/UNKNOWN이면 최신 scheduledAt으로 ZADD한다. 유효한 실행 임대가 있으면 max(scheduledAt, processingDeadline)으로 재노출을 늦춘다.
5. 종료/REVIEW_REQUIRED면 ZREM한다.
6. Redis 성공 응답 후 같은 트랜잭션에서 PUBLISHED로 바꾼다. Redis 실패면 PENDING을 유지하고 전달 백오프를 저장한다.

예약·결과 저장은 주문 → Payments → Outbox, 발행은 Payments → Outbox 순서다. Publisher는 주문을 잠그지 않는다. Redis I/O 동안 이 짧은 트랜잭션의 잠금은 유지하며 작은 단위와 제한된 Redis 타임아웃을 사용한다. 토스 I/O에는 DB 트랜잭션을 유지하지 않는다.

기존 findPublishableForUpdate는 모든 타입의 Outbox를 먼저 잠근다. 이를 결제 Publisher에서 그대로 사용하지 않는다. **기존 공동구매 Publisher 조회도 자기 이벤트 두 종류만 가져오도록 제한**해야 서로의 이벤트를 가져가거나 잠금 순서를 뒤집지 않는다. 결제 타입을 enum에 추가하는 것만으로 연결이 끝나지 않는다.

이 방식은 같은 결제의 재예약과 발행이 DB에서 직렬화되므로 이전 Publisher가 새로운 PENDING을 뒤늦게 PUBLISHED로 덮는 것을 막는다.

### 9.4 전달 보장과 중복

- DB 커밋 전 종료: Payments와 Outbox 변경 모두 롤백.
- DB 커밋 후 Redis 전송 전 종료: PENDING Outbox를 다음 Publisher가 발행.
- Redis 성공 후 DB 커밋 실패: 같은 최신 예약을 재발행할 수 있음.
- Redis 응답 타임아웃: 실제 반영 여부를 알 수 없으므로 PENDING 유지.

따라서 적어도 한 번 전달을 지향하며 정확히 한 번 전달을 보장하지 않는다. 같은 member의 중복 ZADD가 항목을 늘리지는 않지만 임시 가시성 score를 바꿀 수 있다. 워커는 DB 소유권과 최신 Outbox.scheduledAt을 반드시 검증한다.

네트워크 지연으로 오래된 명령이 뒤늦게 반영되는 경우까지 DB 잠금으로 막을 수는 없다. 조기 노출은 DB의 최신 예약 시각으로 차단하고, 누락·늦은 score는 재동기화로 복구한다. Redis 명령 결과만으로 결제를 성공·실패 처리하지 않는다.

### 9.5 Redis 유실과 복원

PUBLISHED는 Redis에 영구 보관됐다는 뜻이 아니다. 발행 완료 후 Redis가 유실되면 PENDING 재시도만으로는 복구하지 못한다.

- 자동 실행 가능한 PENDING/UNKNOWN 결제의 Outbox는 PUBLISHED여도 삭제하지 않는다.
- 정기 보완과 Redis 복구 시, 실행 가능한 결제 및 최신 Outbox를 페이지 단위로 순회한다.
- 같은 Payments → Outbox 잠금 순서로 현재 상태를 재확인하고, 누락/불일치한 예약의 Outbox를 PENDING으로 되돌린다. 보완 작업이 독립적으로 오래된 ZADD를 수행하지 않는다.
- PENDING 전달 실패 건은 기존 nextAttemptAt 백오프를 유지한다. 보완 작업이 매번 이를 현재 시각으로 당기지 않는다.
- Publisher가 현재 scheduledAt과 유효 임대를 기준으로 큐를 복원한다. 종료·REVIEW_REQUIRED는 재등록하지 않는다.
- 키셋 페이지네이션으로 뒤쪽 이벤트까지 순회한다. 매번 같은 앞부분만 확인하지 않는다.
- 성사 주문에 Payments 자체가 없는 경우는 별도 주문 보완으로 Payments+Outbox를 함께 생성한다.

Outbox가 없는 기존 미완료 Payments는 추측으로 즉시 재청구하지 않는다. 전환 절차에서 기존 예약 시각을 이관하거나 REVIEW_REQUIRED로 분류한다.

종료 결제의 Outbox 정리는 Redis 삭제가 반영된 뒤 보존 기간 정책에 따라 한다. 삭제 전 장기 지연 Publisher와 복구 작업이 없는지 고려한다. 자동 실행 가능한 행의 최신 예약을 일반 이벤트 보존 기간만으로 삭제하지 않는다.

Redis 장애 중에는 신규 워커 실행을 멈추고 Outbox를 누적한다. 이미 실행 중인 결제 결과는 DB와 Outbox에 저장한다. DB 큐 실행으로 자동 전환하지 않는다.

## 10. 클래스 책임

작은 역할마다 별도 서비스 클래스를 추가하지 않는다. 기존 Outbox 구조를 활용해 아래 책임으로 묶는다. 기존 클래스의 합치기·이름 변경은 실제 구현 시 진행한다.

| 구성 | 책임 |
| --- | --- |
| PaymentService | 주문 기반 결제 예약과 기존 결제수단 기능 |
| GroupPaymentReservationService | 성사 판정 커밋 후 해당 공동구매 주문을 페이지 단위로 즉시 예약 |
| PaymentExecutionService | 짧은 DB 트랜잭션: 실행 선점·검증, 결과 저장, 재예약 |
| PaymentSchedule | Redis 등록·후보 확보 Lua·삭제를 캡슐화하는 infrastructure 컴포넌트 |
| PaymentWorkerPool | 전용 Executor와 가용 슬롯 관리, 스케줄러의 작업 제출 및 종료 관리 |
| PaymentWorker | 슬롯 안에서 Redis 후보 조회 → DB 선점 → 외부 호출 → 결과 저장 실행 |
| PaymentOutboxPublisher / 발행 서비스 | 결제 타입 전달·백오프·Redis 재동기화 |

토스 HTTP 클라이언트는 기존 것을 재사용한다. PaymentWorker가 DB 서비스의 트랜잭션 메서드를 호출하고, 반환 후 외부 호출을 한다. 같은 객체의 메서드 호출에 트랜잭션 프록시가 적용될 것이라고 가정하지 않는다.

### 실행 모델: 기본 1개, 설정으로 확장

워커 풀 크기는 **기본 1로 확정**한다. corePoolSize와 maximumPoolSize를 동일한 설정값 N으로 구성하는 고정 크기 풀을 사용한다. 가상 스레드는 사용하지 않는다. 설정 변경은 우선 재시작 시 반영하는 것으로 하고 실행 중 동적 증감은 범위에 넣지 않는다.

```text
스케줄러의 주기적 호출
  → 가용 슬롯이 없으면 이번 주기 종료
  → 슬롯 확보 후 전용 Executor에 실행 작업 제출
  → 스케줄러는 토스 응답을 기다리지 않고 반환

결제 워커(기본 1개)
  → Redis에서 도래한 후보 1건 확보
  → DB 실행 소유권 검증·선점
  → 토스 조회 또는 청구
  → 결과와 Outbox 변경 저장
  → finally에서 슬롯 반환
```

- 스케줄러는 후보를 미리 조회·선점하지 않는다. 실행 슬롯이 확보된 워커가 직접 조회한다.
- 현재 N=1이면 실행 중에는 추가 작업을 제출하지 않는다. N으로 확장하면 가용 슬롯 수만큼만 제출한다. 단일 실행 중 boolean 대신 슬롯 수로 제어한다.
- 결제 대기열은 Redis다. Executor에는 결제 작업을 장시간 쌓는 메모리 대기열을 두지 않는다. 직접 전달 방식의 제출 거절은 정상적으로 처리한다.
- 제출 실패·거절 시 확보한 슬롯을 즉시 반환한다. CallerRunsPolicy처럼 결제 작업을 스케줄러 스레드에서 대신 실행하는 정책은 사용하지 않는다.
- 후보가 없거나 예외가 발생해도 워커는 finally에서 슬롯을 반환한다. 처리되지 않은 후보는 Sorted Set 재노출과 DB 임대 만료로 복구한다.
- 종료 시 신규 제출을 먼저 막고 실행 중 작업을 제한 시간 동안 기다린다. 미완료 요청을 강제 종료했다고 토스에서도 취소됐다고 간주하지 않는다.
- Outbox 발행·보완은 결제 슬롯을 점유하지 않는 제한된 주기 작업으로 둔다. 이들의 DB·Redis I/O 자체까지 스케줄러에서 사라지는 것은 아니다.

전체 동시 실행 수는 인스턴스 수 × N이다. 확장 시 DB 연결 풀, 토스 응답 시간·허용 처리량을 확인한다. 소유권 검사와 멱등키는 풀 크기에 관계없이 적용하므로 워커 수 증가를 이유로 결제 안전성 로직을 새로 도입하지 않는다.

기존 판정 스케줄러는 직접 순차 처리하지만 결제는 외부 응답 대기가 있으므로 실행 위치를 분리한다. 서비스 클래스만 분리하고 동기 호출하는 것은 실행 스레드를 분리한 것이 아니다. 기본 풀 크기 1은 병렬 결제 없이도 판정 등 다른 스케줄러 작업이 토스 응답을 기다리지 않도록 하기 위한 선택이다.

## 11. 장애 검증과 운영

| 검증 상황 | 기대 결과 |
| --- | --- |
| 여러 워커의 동시 Lua 실행 | 한 후보를 정상적으로 연속 배정하지 않음 |
| 같은 paymentId 강제 중복 전달 | 유효한 DB 소유자만 실행 시작 |
| Lua 실행 후 응답 유실 | 삭제되지 않은 후보가 다시 도래 |
| DB 커밋 후 ZADD 실패 | PENDING Outbox 재발행 |
| Redis 반영 후 PUBLISHED 저장 실패 | 재발행해도 청구 중복을 DB가 차단 |
| 재예약과 Publisher 동시 실행 | 최신 예약의 PENDING 상태가 유실되지 않음 |
| 기존 공동구매 Publisher와 동시 실행 | 각자 자기 이벤트 타입만 처리 |
| PUBLISHED 이후 Redis 전체 유실 | 보존된 최신 Outbox로 재발행 |
| 승인 후 응답 유실·DB 저장 실패 | UNKNOWN 조회 복구, 새 멱등키 사용 금지 |
| 재예약·삭제 명령 지연과 역전 | DB 상태·실행 시각 검증으로 잘못된 청구 차단 |
| 임대 만료 후 이전 워커 완료 | 새 토큰의 결제·주문 상태를 덮어쓰지 못함 |
| Redis 전체 유실 | 예약 가능한 DB 데이터로 전체 큐 복원 |
| 수동 확인·성공 건이 Redis에 남음 | 실행하지 않고 제거 |
| Executor 포화·종료 | 슬롯 확보 전 후보를 가져오지 않음 |
| 풀 크기 1에서 여러 번 스케줄 호출 | 결제 실행은 최대 1개, 실행 대기 작업이 누적되지 않음 |
| 토스 응답 지연 | 스케줄러는 작업 제출 후 반환하며 직접 결제를 실행하지 않음 |
| 제출 거절·워커 예외·후보 없음 | 슬롯을 반환하고 스케줄러에서 대신 실행하지 않음 |
| 풀 크기 N 또는 다중 인스턴스 | 서로 다른 결제는 병렬 처리, 같은 결제의 유효한 DB 실행 소유자는 하나 |

큐 길이·실행 지연, UNKNOWN·수동 확인 건수, DB/Redis 보완 지연, 토스 오류, Redis Lua 지연을 관찰한다. 결제 토큰·methodKey·customerKey·요청 원문을 로그에 남기지 않는다.

Redis의 영속성·복제·메모리 정책은 환경에 맞게 설정한다. DB 복원이 있다고 Redis 장애를 무시하지 않는다. 기존 Redis와 함께 사용하면 공동구매 판정 등 다른 기능과 장애·메모리 영향을 공유한다.

## 12. 과거 DB 큐 구현을 기준으로 한 전환 순서와 한계

이 절은 DB 큐가 이미 배포된 환경을 위한 참고다. 현재 저장소의 DB 큐 작업은 롤백됐으므로 실제 구현 시작점과 작업 순서는 아래 13절을 우선한다.

1. 기존 DB 큐 워커를 비활성화한다. 진행 중 요청 종료를 기다리거나 임대 만료와 UNKNOWN 복구를 확인한다.
2. 기존 Payments와 멱등키를 보존한다. Redis 도입을 이유로 새 결제나 새 키를 만들지 않는다.
3. 결제 Outbox 타입·재예약 갱신·전용 Publisher와 기존 Publisher 타입 필터를 구현한다. Redis 예약 컴포넌트와 paymentId 기반 DB 실행 선점을 연결한다.
4. 기존 Payments.nextAttemptAt을 Outbox.scheduledAt으로 이관한다. 기존 UNKNOWN 중 예약·선점이 없는 건은 REVIEW_REQUIRED로 전환하고 유니크 제약도 보완한다. 전환 검증 후 별도 마이그레이션으로 Payments.nextAttemptAt을 제거한다.
5. 최초 DB → Redis 보완을 실행한다. 수동 확인 건은 등록하지 않는다.
6. 결제 전용 풀 크기를 1로 설정하고 검증한다. 토큰·데드라인 검증을 유지한 채 필요할 때 설정값을 늘린다.
7. 기존 DB 전역 폴링 실행 경로를 제거한다. 과거 Flyway 파일은 수정하지 않는다.

Stream의 ACK·소비자 그룹·pending 관리 대신, 이 안은 단일 Sorted Set의 재노출과 기존 DB 소유권 검증을 사용한다. Redis 자료구조는 하나지만 장애 처리가 없어지는 것은 아니다.

DB 큐보다 줄어드는 것은 정상 경로의 DB 실행 후보 탐색이다. 추가되는 것은 Redis 후보 가시성 처리, 상태 기반 Outbox 발행과 재동기화다. 결제 상태, 동일 요청 재전송, 결과 불명확 처리, 늦은 결과 차단은 어느 큐를 쓰든 유지한다. 이 비용을 명시적으로 받아들이는 설계다.

## 13. 구현 작업 지침

이 절은 구현과 유지보수의 기준이다. 앞 절의 선택 표현과 충돌하면 이 절의 구체적인 결정을 우선한다.

### 13.1 시작점과 범위

- 현재 저장소는 DB 큐 도입 전 동기 자동결제 코드로 복원된 상태다. PaymentQueueService, PaymentWorkerPool, PaymentClaim, V16/V17 큐 마이그레이션은 없다. 삭제된 구현이 있다고 가정하거나 백업을 통째로 복원하지 않는다.
- 현재 최종 마이그레이션 파일은 V15다. 실제 작업 시작 시 git 상태와 마이그레이션 목록을 다시 확인한다. 기존 파일이나 사용자 변경을 덮어쓰지 않는다.
- 구현 대상은 최초 결제 예약, Outbox 전달, Redis 후보 확보, 워커 실행, 결과 반영, 재시도·복구다. 주문 취소·공동구매 기존 정책을 임의로 바꾸지 않는다.
- 결제수단 등록·조회·삭제, 브랜드페이 토큰 발급·갱신, 기존 공동구매 Redis 키와 주문 생성 Stream은 보존한다.
- 결제용 Stream, 추가 Redis 처리 큐, 분산 락 서비스, 범용 작업 프레임워크, 수동 재청구·환불 API는 추가하지 않는다.
- 정상 실행 후보를 DB 전체 폴링으로 가져오는 별도 결제 경로는 만들지 않는다. DB 스캔은 주문 예약 보완·Redis 복원에만 사용한다.
- 기능은 기본 비활성으로 구현한다. 개발·운영 DB의 마이그레이션 적용이나 실제 토스 청구는 별도 승인 없이 실행하지 않는다.

### 13.2 파일별 변경 계획

기준 패키지는 `com.moongcheap_backend`다. 아래 역할을 기준으로 기존 파일을 재사용하고 중복 서비스를 남기지 않는다.

| 대상 | 작업 |
| --- | --- |
| payments/application/PaymentService | 결제수단 기능 보존. scheduleAutomaticPayment(orderId) 추가. 기존 executeAutomaticPayment는 예약만 하는 호환 메서드로 변경하고 동기 완료 의미가 달라짐을 주석으로 명시 |
| payments/application/PaymentPreparationService | prepare와 Preparation을 예약용 schedule 계약으로 교체. Payments와 Outbox를 함께 생성·커밋 |
| payments/application/PaymentCompletionService | 완료 처리 책임을 새 PaymentExecutionService에 통합. 모든 호출자·테스트를 옮긴 후 이 클래스 제거 |
| payments/application/PaymentExecutionService | 실행 선점, 재전송 허용 확인, 결과 저장, 오류 처리의 짧은 DB 트랜잭션 전담 |
| payments/application/PaymentWorkerPool | 전용 고정 크기 Executor, 가용 슬롯, 제출 스케줄, 종료 관리 |
| payments/application/PaymentWorker | 후보 1건 조회부터 외부 요청과 결과 저장까지 조율. DB 트랜잭션을 시작하지 않음 |
| payments/application/PaymentQueueProperties | 13.7 설정을 바인딩·검증 |
| payments/infrastructure/PaymentSchedule | StringRedisTemplate과 Lua로 등록·후보 확보·삭제·score 조회 구현 |
| payments/application/PaymentOutboxPublisher, PaymentOutboxPublishService | 스케줄/후보 조회와 이벤트별 트랜잭션 발행을 별도 빈으로 분리 |
| payments/application/PaymentRecoveryService | 미예약 주문 보완과 Redis 재동기화. 페이지 순회와 개별 트랜잭션을 구분 |
| payments/domain/Payments, enums/PaymentsStatus | 실행 필드 4개, 고정 요청 정보, 로컬 상태와 도메인 전이 메서드 추가 |
| payments/infrastructure/PaymentsRepository, order/infrastructure/OrdersRepository | paymentId 잠금·재사용 조회와 미예약 주문 키셋 페이지 조회 추가 |
| common/outbox/domain/OutboxEvent, OutboxEventType | PAYMENT_SCHEDULE_SYNC 팩토리·재예약·재동기화 메서드 추가. 공동구매 동작은 보존 |
| common/outbox/infrastructure/OutboxEventRepository | 타입별 후보 조회, paymentId 기반 결제 Outbox 조회·잠금, 복원 페이지 조회 추가 |
| groupbuy/application/GroupBuyOutboxPublishService 및 관련 테스트 | 기존 두 타입만 조회하도록 수정. 결제 이벤트를 가져가지 않게 함 |
| payments/infrastructure/BrandPayPaymentClient, TossBrandPayPaymentClient | 기존 청구 요청 형식 보존. 조회는 PaymentReconciliationClient로 분리하고 같은 Toss 구현체에서 구현 가능 |
| payments/infrastructure/PaymentGatewayException | 원문 없이 안전한 코드와 분류만 전달하는 구조화 예외 추가 |
| application.yml 및 새 Flyway 파일 | 기본 비활성 설정, 아래 스키마 변경과 데이터 전환 |

성사 판정의 직접 연결은 `GroupBuyJudgmentScheduler`에서 수행한다. `judgeAndPay()`가 boolean 성사 결과를 반환하고 트랜잭션이 커밋된 다음에만 `GroupPaymentReservationService`가 해당 공동구매의 주문을 페이지 단위로 schedule한다. GroupBuyJudgmentService의 판정 트랜잭션 안에서는 토스·Redis 호출이나 주문 결제 생성을 수행하지 않는다. 직접 예약 실패와 프로세스 종료는 제한된 주기적 보완이 처리한다.

### 13.3 메서드와 트랜잭션 계약

메서드명은 아래 계약을 기준으로 통일한다. DTO는 불변 record로 만들고 엔티티·인증 정보가 로그에 출력되지 않게 한다.

| 메서드 | 입력 / 반환 | 트랜잭션과 동작 |
| --- | --- | --- |
| PaymentPreparationService.schedule | orderId → paymentId | REQUIRES_NEW. 주문 잠금, 기존 이력 재사용, 신규 Payments+Outbox 동시 저장. 토스/Redis 호출 없음 |
| PaymentSchedule.claimDue | 없음 → Optional paymentId | Lua로 후보 1건의 재노출 시각 이동. DB 작업 없음 |
| PaymentExecutionService.begin | paymentId → Optional Execution | REQUIRES_NEW. 상태·예약·임대 검증 후 토큰/UNKNOWN/횟수 증가를 커밋. 실행 불가면 빈 결과 |
| PaymentExecutionService.allowReplay | paymentId, token → boolean | REQUIRES_NEW. 소유권·deadline·기간·수단 재검증. 허용해도 횟수는 추가 증가하지 않음 |
| PaymentExecutionService.complete | paymentId, token, 응답 → void | REQUIRES_NEW. 주문·결제·Outbox 원자적 변경. 이전 토큰이면 아무것도 변경하지 않음 |
| PaymentExecutionService.handleError | paymentId, token, 오류분류, 복구실행 여부 → void | REQUIRES_NEW. 확정 실패/재예약/수동 확인 전환 및 Outbox 동시 갱신 |
| PaymentWorker.runOne | 없음 → void | NEVER. claimDue → begin → 토스 조회/청구 → complete/handleError. 작업은 최대 1건 |
| PaymentOutboxPublishService.publishOne | eventId → void | REQUIRES_NEW. Payments → Outbox 잠금, 상태 재검사, Redis 반영 후 발행 결과 커밋 |

Execution에는 paymentId, processingToken, 고정 AutomaticPaymentRequest, idempotencyKey, reconciliation 여부만 담는다. begin이 반환되기 전에 실제 DB 커밋이 끝나야 한다. 상위 빈을 통해 호출하고 같은 클래스 내 호출로 트랜잭션 프록시를 우회하지 않는다.

schedule은 기존 결제가 FAILED/REVIEW_REQUIRED여도 새 시도를 자동 생성하지 않는다. 최초 로컬 검증 실패는 외부 청구 없이 실패 이력을 저장하고 주문을 PAYMENT_FAILED로 정리해 같은 주문이 보완 작업의 앞쪽을 계속 점유하지 않게 한다. 조회 페이지 한 건의 오류가 나머지 주문을 영구 차단하지 않게 한다.

### 13.4 경합·실패 동작 확정

- 주문 상태를 변경하는 트랜잭션은 주문 → Payments → Outbox 순서로 잠근다. begin도 로컬 검증 실패로 주문을 종료할 수 있으므로 같은 순서를 사용한다.
- Publisher/재동기화는 Payments → Outbox 순서이며 주문을 잠그지 않는다. 양쪽은 같은 결제의 변경을 직렬화한다.
- DB 잠금 대기는 트랜잭션 로컬 lock_timeout 1초로 제한한다. 잠금 실패는 해당 트랜잭션을 롤백하고 다음 주기에 재시도한다. 실패한 PostgreSQL 트랜잭션 안에서 예외를 잡고 DB 작업을 계속하지 않는다. 후보 조회 방식과 테스트도 이 선택으로 통일한다.
- 유효한 임대가 있으면 begin은 토스를 호출하지 않는다. 만료됐으면 새 UUID로 교체한다. 생성 시각 기준 기간 또는 횟수를 초과하면 REVIEW_REQUIRED와 Outbox PENDING을 함께 저장한다.
- begin이 미래 scheduledAt/유효한 임대로 실행을 거절할 때 최신 예약 반영이 필요하면 같은 트랜잭션에서 Outbox 재동기화를 요청한다. 기존 전달 실패의 백오프는 앞당기지 않는다.
- Redis에만 존재하는 paymentId는 DB에서 실제 삭제 여부를 확인한 뒤 제거한다. DB 조회 실패를 삭제로 취급하지 않는다. paymentId는 재사용하지 않는 전제다.
- Outbox가 없는 미완료 결제는 REVIEW_REQUIRED로 전환한다. 필요한 경우 삭제 전달용 Outbox를 함께 생성하되, 청구 예약으로 복원하지 않는다.
- UNKNOWN 복구의 조회 결과가 명시적으로 없을 때만 allowReplay를 거쳐 동일 POST를 보낸다. 조회 장애를 미조회로 바꾸지 않는다.
- 현재 토큰에 대한 결과 저장은 deadline이 지났더라도 아직 교체되지 않았다면 허용한다. 교체됐으면 성공·실패·재예약·주문·Outbox 모두 변경하지 않는다.
- complete의 DB 저장 실패를 토스 실패 예외로 분류하지 않는다. 임대 만료 후 UNKNOWN 조회로 복구한다. finally에서 Redis 항목을 무조건 삭제하지 않는다.
- HTTP 4xx 전체를 확정 실패로 보지 않는다. 공식 의미를 확인한 승인 거절 코드만 분류하고, 나머지는 불명확 또는 설정 오류로 처리한다. 복구 실행 중 거절은 이전 승인 여부를 확정하지 못하므로 수동 확인으로 넘긴다.
- Redis 스크립트는 영속 예약이 아니라 후보 가시성만 변경한다. 최신 DB 예약·상태 검증은 생략하지 않는다.

조회 API의 경로·인증 방식, 명시적인 미조회 코드, 멱등키의 범위·유효 기간은 구현 시 토스 공식 문서 또는 공식 MCP로 확인하고 검증 근거를 남긴다. 실제 결제를 보내는 것으로 규격을 확인하지 않는다.

### 13.5 마이그레이션과 기존 데이터

현재 파일 기준 신규 버전은 V16부터다. **적용 대상 DB에 이전 작업의 V16/V17이 이미 적용돼 있다면 같은 버전을 다른 내용으로 재사용하지 않는다.** flyway_schema_history를 확인하고 사용자와 별도 전환을 결정한다. 체크섬 오류를 repair로 숨기거나 이력을 삭제하지 않는다.

1. Payments에 attempt_count(default 0), processing_token(UUID), processing_deadline(timestamptz), idempotency_key를 추가한다. next_attempt_at은 추가하지 않는다.
2. 고정 수단 참조와 customerKey 스냅샷을 추가한다. 금액·주문번호·상품명·수단 유형은 기존 필드를 고정해서 사용한다. methodKey는 불변 참조로 보존한다.
3. 기존 DONE → SUCCEEDED, ABORTED/EXPIRED → FAILED, CANCELED → CANCELED로 변환한다. READY/IN_PROGRESS는 이미 송신했을 가능성이 있으므로 REVIEW_REQUIRED로 전환하고 자동 실행 Outbox를 생성하지 않는다. 알 수 없는 상태도 임의 PENDING 전환하지 않는다.
4. 기존 BRANDPAY 이력의 멱등키는 현재 BrandPayIdempotencyKeyGenerator.forAutomaticPayment(orderNo)의 계산 결과와 정확히 일치하게 보존한다. 신규 결제는 최초 생성 때 키를 한 번 생성해 저장한다. 기존 요청에 새 UUID를 부여하지 않는다.
5. 주문당 PENDING/UNKNOWN/REVIEW_REQUIRED/SUCCEEDED 중 한 건만 허용하는 부분 유니크 인덱스, 멱등키 유니크 제약, 토큰·deadline의 동시 null 여부 제약을 추가한다. 중복 기존 데이터가 있으면 마이그레이션을 실패시켜 확인하며 임의 삭제하지 않는다.
6. Outbox의 ck_outbox_event_type CHECK에 PAYMENT_SCHEDULE_SYNC를 추가한다. 기존 두 이벤트 타입과 (event_type, aggregate_id) 유니크 제약을 유지한다. 필요하면 결제 복원 조회용 인덱스를 추가한다.
7. 로컬 상태 enum 변경은 구버전 애플리케이션과 동시에 실행하지 않는다. 실행 중 청구가 없는 배포 경계를 확보한다.

Outbox의 scheduledAt/nextAttemptAt은 기존 TIMESTAMP/LocalDateTime 형식을 유지하고 현재 공동구매와 동일하게 Asia/Seoul로 해석한다. DB now는 Instant 기준으로 받고 명시적으로 변환한다. 기존 createdAt의 기록 시간대는 별도로 확인하며 불명확한 기존 건을 자동 재청구하지 않는다. 서버별 기본 시간대 차이로 재전송 기간이 바뀌지 않게 한다.

### 13.6 Outbox 및 복구 세부 규칙

- 같은 결제의 예약 변경과 상태 전이는 반드시 Payments 잠금 아래에서 수행한다. 결제당 Outbox 한 행이므로 재시도마다 insert하지 않는다.
- PENDING Outbox를 후보 ID로 조회하는 단계와 publishOne 트랜잭션은 분리한다. 잠근 후 이미 PUBLISHED이거나 전달 시간이 미래면 종료한다.
- 기존 공동구매 Publisher의 조회 타입 필터를 먼저 수정한다. 결제 Publisher도 자기 타입만 조회한다.
- Redis I/O 예외는 현재 Outbox를 PENDING으로 두고 기존 scheduleRetry 방식으로 백오프한다. DB 장애는 발행 트랜잭션 전체를 롤백한다. Redis 성공 뒤 DB 롤백으로 중복 발행될 수 있음을 테스트한다.
- 누락 복원은 PUBLISHED 예약도 대상으로 한다. 실행 가능한 결제의 최신 Outbox는 삭제하지 않는다. 단순히 “발행 후 N일 경과”로 정리하는 기능을 추가하지 않는다.
- 보완 작업은 한 주기 최대 100건으로 시작하며 ID 커서를 다음 주기로 이어서 전체를 순회한다. 프로세스 재시작 시 처음부터 재검사해도 멱등적이어야 한다.
- score가 달라도 후보 확보 직후의 정상 visibilityDelay일 수 있다. 이를 즉시 낮춰 무한 재노출하지 않는다. 현재/예정 실행 시각과 유효 임대, visibilityDelay 허용 구간을 비교하고, 정상 임시 가시성 지연은 그대로 둔다. 누락이나 허용 범위를 벗어난 오래된 score만 재동기화한다. 정확한 시각 복구보다 제한된 재노출 지연을 허용한다.

### 13.7 초기 설정값

prefix는 moongcheap.payments.queue로 통일한다. 별도 가상 스레드 설정이나 전역 스케줄러 풀 변경은 하지 않는다.

| 설정 | 기본값 / 의미 |
| --- | --- |
| enabled | false. PAYMENT_QUEUE_ENABLED로 활성화. 워커·발행·보완 스케줄을 함께 제어 |
| workers | 1. core/max 동일, 1 이상으로 검증, 변경은 재시작 시 반영 |
| poll-delay-ms | 1000. 실행 슬롯 확인 주기 |
| visibility-delay | 60s. Redis 후보 임시 재노출 지연 |
| lease | 60s. DB 실행 소유권 유효 시간 |
| outbox-publish-delay-ms | 1000. 결제 Outbox 발행 주기 |
| recovery-delay-ms | 5000. 주문 예약·Redis 보완 주기 |
| batch-size | 100. 발행 후보/보완 페이지 상한. 워커는 한 작업당 1건 |
| max-attempts | 5. 조회·청구 통합 실행 횟수 |
| replay-window | 14d. 생성 시각 기준 상한, 공식 규격 확인 후 적용 |
| shutdown-wait | 15s. 종료 시 실행 중 작업 대기 상한 |

토스의 기존 연결 3초/읽기 5초 타임아웃을 유지한다. Redis 발행은 작은 개별 트랜잭션과 유한 타임아웃을 사용한다. 공유 Redis 설정을 무제한 또는 더 긴 값으로 바꾸지 않으며, 기존 설정이 잠금 유지 예산에 부적합하면 결제 전용 접근 설정을 사용한다. lease/visibility 값은 테스트와 운영 지연을 보고 조정하되 무한 외부 대기는 허용하지 않는다.

### 13.8 구현 순서와 완료 기준

1. 현재 호출자·설정·스키마를 확인하고 작업 전 테스트를 실행한다.
2. 엔티티·상태·Outbox 전환 메서드와 마이그레이션을 작성한다.
3. 예약·실행·결과 DB 서비스를 구현하고 단위 테스트한다.
4. Redis Lua, 결제 Publisher와 기존 Publisher 타입 분리를 구현한다.
5. 토스 조회·구조화 오류와 워커 조율을 연결한다.
6. 기본 1개 풀, 포화·거절·종료 처리 및 보완 작업을 구현한다.
7. 직접 청구 우회 경로를 제거하고 기존 테스트를 새 비동기 예약 계약에 맞춘다. 무관한 테스트는 삭제하지 않는다.
8. 실제 PostgreSQL·Redis의 격리된 테스트 환경에서 아래 기준을 확인한다.

필수 검증:

- 예약 중 오류면 Payments와 Outbox가 함께 롤백되고, 성공이면 함께 커밋된다.
- 같은 주문 동시 예약은 동일 결제/Outbox를 재사용한다. REVIEW_REQUIRED는 새 청구로 대체되지 않는다.
- 실제 Redis에서 두 소비자의 후보 확보가 원자적이고, 응답 유실·visibility 만료 후 다시 조회된다.
- 워커 1개는 최대 동시 실행 1, N개는 최대 N이다. 제출 거절·후보 없음·예외 뒤 슬롯이 누수되지 않으며 스케줄러가 토스를 호출하지 않는다.
- DB begin 커밋 후에만 토스가 호출되고, 외부 호출 중 DB 트랜잭션이 없다.
- 임대 회수 후 이전 토큰은 주문·결제·Outbox를 변경할 수 없다.
- 조회→재전송은 1회로 계산하고, 마지막 허용 회차에도 같은 실행의 재전송은 허용한다. 재전송 직전 기간·소유권이 바뀌면 중단한다.
- 토스 모의 응답으로 승인 후 응답 유실, 미조회, 조회 장애, 거절, 금액 불일치와 DB 완료 저장 실패를 재현한다. 동일 본문·멱등키를 검증한다.
- Publisher 성공 후 DB 롤백, 재예약과 발행 경합, 발행 백오프와 보완 경합에서 최신 예약 의도가 사라지지 않는다.
- Redis 전체 유실 후 PUBLISHED Outbox를 포함해 복원하며, 종료/REVIEW_REQUIRED는 실행하지 않는다. 100건을 넘는 뒤쪽 예약도 복원된다.
- 신규 DB 전체 마이그레이션과 기존 상태 데이터 전환을 검증한다. 실제 운영 DB는 사용하지 않는다.
- 기존 결제수단·토큰·공동구매 판정·공동구매 Outbox 회귀 테스트를 통과한다.

완료 보고에는 변경 파일, 테스트 명령·결과, 실행하지 못한 검증, 마이그레이션 및 활성화 주의사항을 적는다. 테스트 성공을 이유로 enabled를 true로 바꾸거나 실제 토스 승인을 실행하지 않는다. 구현과 달라진 설계 문구가 있으면 같은 작업에서 갱신한다.
