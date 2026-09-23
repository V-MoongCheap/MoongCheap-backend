# 브랜드페이 결제 설계 논의 기록

작성일: 2026-09-22

최근 갱신일: 2026-09-23

이 문서는 이 채팅에서 제기한 질문, 설계 변경의 이유, 구현 결과와 미결정 사항을 정리한다. 대화에서 검토한 모든 구조가 구현되거나 최종 채택된 것은 아니다. 구현 현황은 작성 시점의 소스와 대조했다. 외부 API 규격은 대화 당시 확인한 내용을 기록한 것으로, 이 문서 작성 과정에서 새로 검증한 것은 아니다.

## 1. 전체 설계 흐름

```text
브랜드페이 자동결제 사용 결정
  → SDK 인증과 백엔드 토큰 발급 역할 구분
  → 토큰 갱신·멱등성·DB 동시성 논의
  → 토스의 결제수단을 로컬 DB에 동기화
  → 인증 후 동기화를 하나의 백엔드 요청으로 연결
  → 결제수단 목록 조회·삭제 구현
  → 주문 기준 자동결제 구현
  → 토스 호출 전에 Payments를 생성하도록 변경
  → 자동 재시도 실행기가 없다는 점 확인
  → Sorted Set으로 재시도 시각 관리 제안
  → 멀티 워커를 위한 Sorted Set + Stream 제안
  → 최초 결제와 재시도의 실행 경로 통합 논의
  → 분산 상태 관리와 장애 복구의 복잡도 검토
  → 운영 단순화를 위해 PostgreSQL 작업 큐 대안 제안
```

최초 결제와 재시도를 같은 실행 경로로 통합하자는 방향에는 공감했지만, Redis 조합과 PostgreSQL 큐 중 최종 선택 및 구현 지시는 아직 없다.

## 2. 출발점: 브랜드페이 자동결제

사용자는 브랜드페이의 자동결제 기능을 사용할 계획이라고 명시했다. 먼저 결제수단 등록을 만들고, 이후 공동구매 주문에 연결된 수단으로 서버가 결제를 실행하는 순서로 논의했다.

초기에 공유된 토스페이먼츠 AI 연동 안내는 MCP 서버, llms.txt, LLM Quick Reference, Markdown 문서 접근 방법을 소개하는 참고 자료였다. 프로젝트에 이 도구들을 설치했다는 의미는 아니다.

여기서 구분한 핵심 식별자는 다음과 같다.

| 값 | 역할 |
| --- | --- |
| `customerKey` | 브랜드페이 고객 식별자 |
| Authorization Code | SDK 인증 후 백엔드가 토큰 발급에 사용하는 일회성 코드 |
| Access Token / Refresh Token | 고객 정보 접근 및 토큰 갱신에 사용 |
| `methodKey` | 등록된 결제수단을 특정하는 서버용 키 |
| `orderNo` | 토스 요청의 `orderId`로 사용하는 주문번호 |
| `Payments.id` | 로컬 결제 레코드 식별자 |
| `paymentKey` | 토스가 반환하는 결제 식별자 |

`Payments.id`와 토스의 `paymentKey`는 다르다. 토스 호출 전에 생성하는 것은 로컬 `Payments`이며, 이때 `paymentKey`는 아직 없다.

## 3. 결제수단 등록: 프론트와 백엔드 역할

사용자는 SDK와 API 방식의 차이, SDK를 적용할 위치, 백엔드가 만들어야 할 API를 차례로 질문했다.

논의한 역할 분담은 다음과 같다.

1. 프론트엔드가 브랜드페이 SDK를 초기화하고 인증·결제수단 등록 UI를 사용한다.
2. 백엔드는 인증 결과의 `code`와 `customerKey`를 받아 고객 일치 여부를 확인한다.
3. 백엔드가 토스 토큰 발급 API를 호출해 토큰을 저장한다.
4. 백엔드가 토스에서 등록된 결제수단을 조회해 로컬 DB에 동기화한다.

주의: SDK 인증 완료와 결제수단 추가 완료의 시점은 구분해야 한다. 인증 콜백 시점에 신규 수단 등록이 끝났는지는 실제 프론트 SDK 흐름에서 확인해야 한다. 인증 후 조회를 연결했다고 모든 신규 수단이 그 순간 반드시 조회된다는 뜻은 아니다.

### 외부 API 인터페이스 분리

`BrandPayAuthorizationClient`는 호출 규격, `TossBrandPayAuthorizationClient`는 토스 HTTP 호출 구현을 맡도록 나눴다. 서비스가 외부 통신 세부사항에 직접 의존하지 않도록 하고 테스트에서 대체할 수 있게 하는 구조다. 결제수단 조회·삭제와 자동결제에도 같은 구성을 적용했다.

### 코드와 표시명

카드사·은행·증권사 코드를 채우는 과정에서 코드·한글·영문을 나누면 혼동될 수 있다는 문제가 제기됐다. 외부 API용 코드와 사용자 표시명은 목적이 다르므로 사용 위치를 구분해야 한다. 현재 결제수단 목록 응답은 `ProviderCode.getDisplayName()`을 사용한다.

## 4. 토큰 갱신: 멱등성과 DB 동시성의 역할 구분

토큰 발급 이후에는 Access Token 갱신과 만료 확인 기능을 논의하고 구현했다.

```text
토큰 사용 요청
  → 저장된 토큰의 만료 시각 확인
  → 유효하면 복호화한 Access Token 반환
  → 갱신이 필요하면 Refresh Token으로 토스 요청
  → 새 토큰 저장 후 Access Token 반환
```

동시 갱신에 대해 `@Transactional`, `@Version`, Redis 락, 토스 멱등키의 필요성을 검토했다. Redis 락이 이미 추가됐다는 설명에 사용자가 의문을 제기했고, 실제 구현 여부를 분명하게 구분해야 한다는 점이 드러났다. 현재 토큰 경로는 Redis 락을 전제로 하지 않는다.

사용자는 Access Token 자체 또는 해시를 멱등키로 쓰자고 제안했고, 이후 저장된 **암호화 Access Token의 해시**를 사용하는 방향을 선택했다. 현재 갱신 멱등키는 회원 ID와 저장된 암호문을 입력으로 생성한다.

- 토스 멱등키: 같은 외부 요청의 중복 처리를 제어한다.
- `BrandPayToken.@Version`: 오래된 엔티티가 최신 DB 토큰을 덮어쓰는 충돌을 감지한다.
- 짧은 DB 저장 트랜잭션: 외부 응답을 저장한다.

이 장치들은 서로 다른 문제를 해결한다. 멱등키만으로 로컬 DB 동시성까지 해결되는 것은 아니다. 재시도 키는 같은 저장 암호문에서 만들어야 하며, 매번 재암호화한 값으로 만들면 동일성이 깨질 수 있다.

## 5. 결제수단 동기화와 API 통합

토스 결제수단 조회는 등록된 전체 목록을 반환한다. 사용자는 이것이 사용자에게 목록을 그대로 반환하는 API가 아니라, 등록 이후 서버가 결제수단을 가져와 저장하는 기능이라고 명확히 했다.

현재 동기화는 `methodKey`를 기준으로 신규 수단을 등록하고 기존 수단을 갱신한다. 전체 활성 목록에서 사라진 기존 수단은 `EXPIRED`로 처리한다.

초기에는 프론트가 인증 API 이후 동기화 API를 추가 호출하는 구조가 논의됐다. 사용자는 하나로 합치는 것이 합리적이라고 판단했고, 별도의 조합 서비스를 추가하기보다 컨트롤러에서 두 서비스를 순서대로 호출하도록 요청했다.

```text
POST /api/payments/brandpay/authorization
  → BrandPayTokenService.issue(...)
  → CreatePayMethodService.synchronize(...)
  → 204 No Content
```

두 서비스 호출은 하나의 분산 트랜잭션이 아니다. 토큰 발급·저장 후 동기화가 실패하는 부분 성공 상황은 별도 복구 대상으로 남는다.

## 6. 사용자용 목록 조회와 삭제

사용자용 결제수단 조회는 동기화와 별도로 `PaymentService`에 구현했다. `EXPIRED`는 제외하며, 서버용 `methodKey`는 응답에 포함하지 않는다.

삭제는 다음 순서다.

```text
로그인 회원 소유 여부 확인
  → 이미 EXPIRED면 종료
  → 유효한 Access Token 확보
  → 카드·계좌 유형에 맞는 토스 삭제 API 호출
  → 로컬 결제수단 EXPIRED, isDefault=false
  → 204 No Content
```

주문 등의 참조를 보존하기 위해 DB 행을 물리 삭제하지 않는다. 삭제 재요청에는 같은 수단을 식별하는 해시 멱등키를 사용한다.

사용자는 삭제 시 카드·계좌번호도 제거하자고 제안했다. `maskedNumber`를 null로 바꾸는 방안과 과거 결제 표시 정보의 스냅샷 보관을 논의했지만, 현재 `expire()`에는 번호 제거가 구현되지 않았고 컬럼도 nullable=false다.

## 7. 자동결제 구현과 인증 방식 정정

처음에는 기존 토큰 자동 갱신을 자동결제에서도 재사용할 것으로 설명했으나, 토스 규격을 확인한 뒤 바로잡았다.

**현재 브랜드페이 자동결제 실행은 시크릿 키의 Basic 인증을 사용한다.** Access Token을 사용하는 결제수단 조회·삭제와 구분된다.

현재 진입점은 `PaymentService.executeAutomaticPayment(Long orderId)`다. 주문 상태, 결제수단 소유자·활성 상태, 금액 및 상품명을 확인하고, `customerKey`와 `methodKey`로 `POST /v1/brandpay/payments`를 호출한다.

현재 요청 정책은 카드 일시불, 면세 금액 0, 계좌 문화비 여부 false다. 과세·면세 및 문화비 정책이 달라지는 상품까지 일반화한 구현은 아니다.

응답의 주문번호·상품명·금액·DONE 상태를 확인하고, `paymentKey`와 승인 시각이 존재하는지도 검증한다.

참고 규격: [토스 브랜드페이 API](https://docs.tosspayments.com/reference/brandpay)

## 8. 중요한 변경: 토스 호출 전에 Payments 생성

최초 구현은 토스 성공 후에 `Payments`를 생성했다. 사용자가 API 호출 전에 결제 객체가 생성되는지 질문하고 사전 생성을 요청하면서 구조가 바뀌었다.

현재 흐름:

```text
PaymentService.executeAutomaticPayment(orderId)
  → 주문·결제수단 검증
  → PaymentPreparationService.prepare(orderId, method)
      → 주문 행 잠금
      → 기존 READY 또는 DONE 조회
      → READY 재사용 / DONE이면 실행 생략 / 없으면 READY 생성
      → 준비 트랜잭션 커밋
  → 토스 자동결제 API 호출
  → 응답 검증
  → PaymentCompletionService.completeAutomaticPayment(orderId, paymentId, response)
      → 주문 및 결제 행 잠금
      → 결제와 주문의 연결 확인
      → 기존 Payments에 paymentKey·approvedAt 기록, DONE 변경
      → 주문 PAYMENT_COMPLETED 변경
      → 완료 트랜잭션 커밋
```

준비와 완료를 별도 서비스에 둔 이유는 Spring 트랜잭션 경계를 분리하기 위해서다. 현재 비트랜잭션 진입점에서 호출하는 흐름에서는 준비가 커밋된 뒤 토스를 호출한다. `saveAndFlush()` 자체가 커밋을 뜻하지는 않는다. 향후 상위 트랜잭션에서 호출하면 기본 전파 속성으로 합류할 수 있으므로 같은 경계가 유지되는지 확인해야 한다.

현재 오류가 발생하면 결제는 READY로 남는다. 이는 성공 여부를 모르는 상태도 포함하므로 READY만 보고 실제 미결제라고 단정할 수 없다.

## 9. 재시도 가능한 구조와 자동 재시도는 다르다

사용자가 재시도 로직이 아직 없는지 확인했고, 자동 실행기가 없다는 점을 명확히 했다.

현재 갖춘 기반은 기존 READY 재사용, 주문번호 기반 고정 멱등키, 완료 여부 확인이다. 누군가 같은 주문으로 메서드를 다시 호출할 수는 있지만, 스스로 실패 건을 찾아 다시 호출하지는 않는다.

아직 없는 것은 예약 시각, 시도 횟수, 오류 분류, 백오프, 워커 장애 회수, 최대 시도 후 처리 정책이다.

## 10. 대안 A: Redis Sorted Set

사용자는 기존 공동구매 판정처럼 Sorted Set으로 재시도를 관리하자고 제안했다. 공동구매 판정과 결제는 ID 의미와 처리 정책이 달라 별도 키를 사용하기로 논의했다.

| 용도 | 키 예시 | member | score |
| --- | --- | --- | --- |
| 기존 공동구매 판정 | `moongcheap:group-buy:judgment:pending` | groupBuyId | 판정 시각 |
| 제안한 결제 실행 예약 | `moongcheap:{auto-payment}:retry` | paymentId | 실행 시각의 epoch milliseconds |

같은 Redis를 사용할 수 있으며, DB 번호까지 분리할 필요는 없다는 방향이었다. Redis에는 식별자와 실행 시각을 최소한으로 넣고, 결제 상태와 실제 요청 정보는 DB를 기준으로 한다.

Sorted Set 단독으로 병렬 처리하려면 작업 선점과 워커 장애 복구를 별도로 설계해야 한다.

## 11. 대안 B: Sorted Set + Stream + Outbox

사용자가 멀티 스레딩을 고려하면서 Sorted Set과 Stream의 역할을 나누는 안으로 확장했다.

```text
DB: Payments + 예약 Outbox를 함께 커밋
  → Publisher: Sorted Set에 실행 시각 등록
  → Dispatcher: 실행 시각에 도달한 항목을 Stream으로 이동
  → Consumer Group: 여러 워커에 작업 분배
  → 워커: 토스 호출 및 DB 결과 저장
  → 처리 결과가 안전하게 저장되면 XACK
```

제안한 Redis 데이터 구성:

| 자료구조 | 내용 |
| --- | --- |
| Sorted Set | member=`paymentId`, score=`nextAttemptAt` |
| Stream | `paymentId`, `scheduledAt`, `dispatchedAt` |
| Consumer Group | `automatic-payment-workers` |
| Consumer | 인스턴스·워커별 고유 이름 |

Stream의 시간 필드는 모니터링 목적이다. 재시도 횟수·금액·인증 정보는 메시지에 복제하지 않고 DB에서 읽는 방향이었다. Stream 메시지 ID는 ACK와 Pending 관리용이며 결제 ID나 토스 멱등키로 쓰지 않는다.

Sorted Set에서 제거한 뒤 Stream에 넣는 사이의 장애를 줄이기 위해 Lua를 통한 원자적 이동을 제안했다. Cluster 사용 시에는 두 키에 같은 `{auto-payment}` 해시 태그를 적용한다. 다만 Lua의 원자적 실행이 임의의 실행 오류까지 트랜잭션처럼 롤백한다는 의미는 아니므로, 오류 검증과 재발행 시 중복 방어가 필요하다.

Stream의 Pending 회수도 정확히 한 번 처리를 보장하지 않는다. 토스 멱등키, DB 선점과 상태 검증은 계속 필요하다. Outbox와 Redis 유실 후 DB 기반 복구까지 필요해지면서 운영 요소가 늘었다.

## 12. paymentId 실행과 최초 결제 통합

사용자가 중요한 불일치를 지적했다. 큐에는 `paymentId`를 넣겠다고 했지만 현재 실행 메서드는 `orderId`를 받는다.

이에 준비와 실행을 분리하는 방향을 논의했다.

```java
// 제안한 역할이며 현재 구현된 API가 아니다.
Long scheduleAutomaticPayment(Long orderId);
void executeAutomaticPaymentByPaymentId(Long paymentId);
```

이어서 사용자는 최초 결제도 같은 흐름을 쓰자고 제안했다. 최초 예약은 현재 시각, 재시도 예약은 백오프 이후 시각으로 설정하고 같은 워커가 처리한다.

```text
최초 결제: READY + nextAttemptAt=현재
재시도:   READY + nextAttemptAt=미래
                    ↓
             동일한 paymentId 실행기
```

실행 요청의 금액·상품명은 앞으로 `Payments`에 저장된 스냅샷을 기준으로 하는 방향을 제안했다. 현재 코드는 여전히 `Orders`에서 요청 값을 구성한다.

## 13. 검토 과정에서 드러난 문제와 설명 보완

### 결과 불명확과 확정 실패를 구분해야 한다

토스 승인 후 응답이 유실되면 서버는 타임아웃을 받더라도 고객은 결제됐을 수 있다. 앞선 대화에서 최대 재시도 횟수 초과를 곧바로 ABORTED로 처리하자고 설명했지만, 결과가 불명확한 건까지 그렇게 처리하면 안 된다. 재시도 중단과 미결제 확정은 다르다. 이런 건은 조회·대사 또는 수동 확인 대상으로 남겨야 한다.

현재 클라이언트는 여러 통신 오류를 공통 비즈니스 예외로 변환하므로, 자동 재시도를 구현할 때 확정 거절과 결과 불명확을 구분할 정보도 보존해야 한다.

### 멱등키의 단위와 유효기간

현재 멱등키는 주문번호 기준이다. 새로운 결제 시도를 구분할 필요가 생기면 `paymentId` 또는 별도 시도 ID 기준의 고정 키를 저장하는 안을 제안했다. 아직 변경하지 않았다.

같은 결제의 통신 재시도에는 같은 키와 같은 요청 내용을 사용한다. 새로운 키로 청구하려면 이전 시도가 실제로 미결제인지 먼저 확정해야 한다. 외부 멱등성 보관 기간 밖의 재요청도 무조건 안전하다고 단정할 수 없다.

### 주문 취소 정책 확정: 공동구매 판정 이전에만 허용

초기에는 주문 검증 후 토스 응답 전까지 사용자가 취소할 가능성을 고려해, 주문 처리 중 상태나 취소 후 환불 정책을 제안했다. 이후 사용자가 **주문 취소는 공동구매 판정 이전에만 가능하다**고 정책을 확정했다.

```text
공동구매 판정 이전: 주문 취소 가능
  → 판정 시 취소 가능 구간 종료
  → 공동구매 성사 확정 후 결제 예약
  → 최초 결제 및 재시도: 주문 취소 불가
```

이 정책이 서버에서 보장되면 결제 실행 중 사용자 주문 취소와의 경합은 주요 설계 대상에서 제외할 수 있다. 취소 경쟁을 해결하기 위한 별도 PAYMENT_PROCESSING 주문 상태나 결제 중 취소 접수 기능을 필수로 추가할 필요는 없다. 결제 워커의 선점·복구를 위한 Payments.IN_PROGRESS는 별개의 이유로 여전히 필요하다.

구현에서는 취소 가능 여부 판단과 실제 취소 반영 사이에 판정이 끼어들지 않도록 판정·취소 경계를 보장해야 한다. 판정 기준이 마감 시각인지 실제 판정 트랜잭션인지도 코드에서 일관되게 적용해야 한다. 이는 정책의 구현 조건이며, 이번 문서 수정에서 해당 코드가 이미 보장된다고 검증한 것은 아니다.

결제수단 삭제 가능 시점과 결제 완료 후 환불 정책은 주문 취소와 별개로 남는다.

### 선점한 워커의 장애와 늦은 응답

IN_PROGRESS로 전환한 워커가 종료되면 임대 만료 후 회수해야 한다. 기존 워커가 뒤늦게 응답을 받는 경우에는 선점 토큰으로 오래된 DB 변경을 차단해야 한다. 이 토큰은 외부 결제 중복까지 막지는 않으므로 토스 멱등키도 함께 유지한다.

### 결제 중복 생성과 변경된 요청 정보

IN_PROGRESS를 도입하면 기존 READY/DONE 재사용 조회에도 반영해야 한다. 주문당 활성 결제 제한을 DB 제약으로 보강하는 방안도 논의했다. 재시도 중 금액이나 결제수단이 바뀌어 동일 멱등키에 다른 요청이 붙지 않도록 스냅샷·수단 고정 정책이 필요하다.

### 공동구매의 일부만 결제된 경우

일부 성공·일부 실패 시 성공 주문만 진행할지, 최소 수량 미달이면 환불할지, 실패 고객에게 수단 변경 기회를 줄지는 아직 결정되지 않았다. 이는 큐 기술 선택과 별개의 비즈니스 정책이다.

## 14. 대안 C: PostgreSQL 작업 큐

사용자가 운영 복잡도를 낮출 다른 구조를 물으면서 PostgreSQL을 작업 큐로 사용하는 안을 제안했다. 이미 사용하는 DB 안에서 예약과 결제 상태를 함께 저장하므로, 결제 작업 전달을 위한 Redis·Outbox 동기화를 줄일 수 있다.

```text
Payments READY + nextAttemptAt 저장
  → 실행 가능한 결제를 FOR UPDATE SKIP LOCKED로 조회
  → 짧은 트랜잭션에서 IN_PROGRESS 및 선점 정보 저장
  → 커밋 후 토스 API 호출
  → 성공: DONE + 주문 완료
  → 재시도 대상: READY + 다음 실행 시각
  → 확정 실패: ABORTED
  → 결과 불명확: 재확인·대사 정책 적용
```

제안한 추가 필드:

| 필드 | 목적 |
| --- | --- |
| `attemptCount` | 시도 추적 횟수 |
| `nextAttemptAt` | 최초 및 재시도 실행 시각 |
| `processingDeadline` | 작업 임대 만료 시각 |
| `processingToken` | 현재 워커 소유권 확인 |
| `lastFailureCode` | 실패 분류와 운영 확인 |

대화 중 `retryCount`를 실제 호출 횟수 의미로 사용했으나, 명칭은 `attemptCount`가 더 명확하다. 다만 호출 직전 DB에서 증가시키더라도 그 직후 프로세스가 죽을 수 있으므로, 실제 토스 수신 횟수와 정확히 일치하는 지표는 아니다.

여러 서버·스레드가 SKIP LOCKED로 작업을 나눠 가져갈 수 있다. 외부 요청 동안 DB 잠금은 유지하지 않는다. 완료·재예약은 현재 processingToken이 일치할 때만 허용한다.

앞선 예시의 '20건 선점 후 4개 스레드에 전달'은 대기 중 작업의 임대가 먼저 만료될 수 있다. 실제 구현에서는 가용 워커 수만큼 선점하거나 워커가 직접 선점하는 방식으로 보완해야 한다.

DB 방식에도 폴링 인덱스, 만료 회수, 호출량 제한과 모니터링은 필요하다. 처리량 한계는 측정 없이 단정하지 않으며, 현재는 운영 단순화를 위한 유력한 대안일 뿐 최종 채택되지 않았다.

## 15. 현재 구현과 제안의 경계

아래 표는 2026-09-22 최초 정리 시점의 스냅샷이다. 이후 변경된 공동구매 판정·결제 연결 상태는 18~21절의 후속 기록을 우선한다.

| 항목 | 작성 시점 상태 |
| --- | --- |
| 토큰 발급·암호화 저장·만료 확인·갱신 | 구현됨 |
| 토큰 @Version 및 암호문 기반 갱신 멱등키 | 구현됨 |
| 인증 API에서 토큰 발급 후 결제수단 동기화 | 구현됨 |
| 결제수단 목록 조회·EXPIRED 제외·삭제 | 구현됨 |
| 삭제 시 maskedNumber 제거 | 논의만 진행 |
| orderId 기준 자동결제 | 구현됨 |
| 토스 호출 전 READY 생성·재사용 | 구현됨 |
| 동일 Payments DONE 갱신 + 주문 완료 | 구현됨 |
| paymentId 기준 독립 실행기 | 미구현 |
| 최초 결제와 재시도의 비동기 경로 통합 | 설계 방향 논의 |
| 결제용 Sorted Set / Stream / Outbox | 제안, 미구현 |
| PostgreSQL 결제 큐 | 최신 대안, 미구현 |
| 시도 횟수·다음 시각·임대·선점 토큰 | 제안, 미구현 |
| 오류 분류·자동 재시도·대사 | 미구현 |
| 공동구매 판정 후 결제 실행 연결 | 미구현 |

`PaymentsStatus` enum에 IN_PROGRESS나 ABORTED가 있다는 것과 실제 상태 전환·복구 로직이 있다는 것은 다르다. 현재 자동결제의 주된 전이는 READY → DONE이다.

## 16. 다음 구현 전에 결정할 사항

1. Redis 조합과 PostgreSQL 큐 중 실행 인프라 선택.
2. 결제 준비·예약과 paymentId 실행기의 분리.
3. 결제 시도 단위, 고정 멱등키 및 요청 스냅샷 정책.
4. 판정 이전에만 주문 취소를 허용하는 확정 정책의 코드 보장 및 결제수단 삭제 정책.
5. 확정 거절·일시 오류·결과 불명확의 분류 및 확인 절차.
6. 시도 횟수·백오프·임대 만료·오래된 워커 방어.
7. 공동구매 일부 결제 실패 시 주문·판매자 처리 정책.

## 17. 주변 작업과 검증 기록

대화 중 Docker Compose 파일을 프로젝트 밖에서 상대 경로로 실행해 파일을 찾지 못한 문제가 있었다. 이는 DB 인증 실패와는 별개의 실행 경로 문제다. DB 연결이 필요한 테스트와 외부 서비스 연결 여부도 함께 확인 대상으로 언급됐다.

이전 구현 응답에서는 결제 모듈 테스트 통과를 보고했고, 전체 테스트 실행에서는 Redis 연결 실패로 contextLoads가 실패했다고 보고했다. 이는 당시 기록이며 현재 환경의 성공 여부를 뜻하지 않는다. 이 문서 작성에서는 결제 API 실행이나 전체 테스트를 재실행하지 않았다.

## 18. 공동구매 주문 생성 Stream의 동작

후속 대화에서는 공동구매 생성 이후 주문을 만드는 Redis Stream과 Consumer Group의 동작을 코드 단위로 확인했다.

메시지가 Stream에서 Consumer Group으로 물리적으로 이동한 뒤 다시 Consumer에게 전달되는 구조는 아니다. 메시지 원본은 Redis Stream에 저장되고, Consumer Group은 그룹이 어디까지 읽었는지와 어떤 Consumer가 어떤 메시지를 처리 중인지 관리하는 논리적인 구독 단위다.

```text
Outbox Publisher
  → Redis Stream에 XADD
  → Consumer Group 기준으로 새 메시지 읽기
  → 해당 Consumer의 Pending 목록(PEL)에 등록
  → 주문 생성
  → 성공하면 XACK
  → Consumer Group의 PEL에서 제거
```

`XACK`은 Consumer Group의 Pending 상태만 제거한다. Stream에 저장된 원본 메시지를 삭제하지는 않는다. 원본 제거에는 별도의 삭제 또는 Stream trimming 정책이 필요하다.

### 오래된 Pending 메시지 회수

`GroupBuyOrderCreationStream.claimStale()`은 Pending 상태를 직접 해제하는 메서드가 아니다. 지정한 유휴 시간 이상 처리되지 않은 Pending 메시지를 최대 배치 크기까지 찾고, 그 소유권을 현재 Consumer로 이전해 재처리할 실제 메시지 목록을 반환한다.

```text
오래된 Pending 조회
  → Pending 메시지에서 RecordId만 추출해 RecordId[] 생성
  → claim(KEY, GROUP, consumerName, minimumIdleTime, recordIds)
  → 현재 Consumer로 소유권 이전
  → 회수된 메시지 목록 반환
  → 재처리 성공 시 ACK
```

다음 코드는 메시지 본문 목록을 만드는 것이 아니라 **회수 대상 메시지 ID 배열**을 만든다.

```java
RecordId[] recordIds = pending.stream()
    .map(message -> message.getId())
    .toArray(RecordId[]::new);
```

이어지는 `claim()`은 이 ID에 해당하고 최소 유휴 시간을 만족하는 메시지의 소유권을 현재 Consumer로 변경하고, 재처리할 메시지 본문을 반환한다. Claim 뒤에도 메시지는 Pending이며, 유휴 시간과 전달 관련 메타데이터가 갱신된다. 최종적으로 `acknowledge()`가 성공해야 PEL에서 빠진다.

현재 `GroupBuyOrderCreationConsumer`의 한 번의 실행 순서는 다음과 같다.

1. 30초 이상 유휴 상태인 Pending 메시지를 최대 20개 회수해 처리한다.
2. 새 메시지를 최대 20개 읽어 처리한다.
3. `groupBuyId`가 없는 복구 불가능한 메시지는 로그를 남기고 ACK한다.
4. 주문 생성이 커밋된 메시지는 ACK한다.
5. 주문 생성에 실패한 메시지는 ACK하지 않아 Pending에 남기고, 다음 회수 주기에 재시도한다.

스케줄은 `moongcheap.group-buy.order-consume-delay-ms` 설정을 사용하며 현재 기본 설정은 1,000ms다. Pending과 신규 조회에 각각 배치 크기 20이 적용되므로 둘 다 가득 찬 경우 한 번의 실행에서 최대 40건을 처리할 수 있다.

## 19. Consumer Group 준비와 로컬 플래그

`ensureConsumerGroup()`은 Redis에서 존재 여부를 별도 조회하는 메서드가 아니다. Consumer Group 생성을 시도하고, 이미 존재해 `BUSYGROUP` 오류가 발생하면 정상 상태로 받아들여 그룹이 준비되었음을 보장한다. 그룹 생성 시작 offset은 `0-0`이므로 그룹 생성 전에 Stream에 있던 메시지도 소비 대상에 포함한다.

```java
private final AtomicBoolean consumerGroupReady = new AtomicBoolean();
```

이 값은 Redis 상태가 아니라 현재 애플리케이션 인스턴스의 JVM 메모리에만 있는 캐시성 플래그다.

| 값 | 의미 |
| --- | --- |
| `false` | 이 JVM이 아직 그룹 준비를 확인하지 않음 |
| `true` | 그룹 생성 또는 기존 그룹 존재를 한 번 확인함 |

`AtomicBoolean`과 동기화 블록은 한 인스턴스의 여러 스레드가 동시에 그룹 생성을 시도하는 것을 막는다. 서버가 여러 대면 인스턴스마다 별도의 플래그가 있다. 따라서 이 값은 Redis Consumer Group의 실제 존재 여부에 대한 영구적인 진실의 원천이 아니다.

Redis 초기화 등으로 플래그는 `true`인데 실제 그룹이 사라질 수 있다. 실제 Stream 연산이 `NOGROUP`을 반환하면 `executeWithConsumerGroupRetry()`가 플래그를 `false`로 되돌리고 그룹을 다시 준비한 뒤 해당 연산을 한 번 재시도한다.

## 20. 공동구매 판정 예약과 실행 순서

공동구매 판정 예약은 Redis Stream이 아니라 Sorted Set을 사용한다. 전체 흐름은 다음과 같다.

```text
공동구매와 Outbox 이벤트를 같은 DB 트랜잭션으로 저장
  → GroupBuyOutboxPublisher가 PENDING Outbox 조회
  → 판정 예약 이벤트를 Redis Sorted Set에 등록
  → GroupBuyJudgmentScheduler가 현재 시각까지 도래한 member 조회
  → DB 행을 잠그고 성공/실패 판정
  → 성사됐다면 DB 커밋 후 결제 예약 연결
  → 완료한 판정 예약을 Sorted Set에서 제거
```

Sorted Set의 구성은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| key | `moongcheap:group-buy:judgment:pending` |
| member | `groupBuyId` 문자열 |
| score | 판정 예정 시각의 Epoch milliseconds |

시간 객체나 시간의 밀리초 부분만 저장하는 것이 아니다. `LocalDateTime`을 `Asia/Seoul` 시간대로 해석하고 `Instant`로 바꾼 뒤, 1970-01-01T00:00:00Z부터 흐른 전체 밀리초 값으로 변환한다.

```java
dateTime.atZone(ZoneId.of("Asia/Seoul"))
    .toInstant()
    .toEpochMilli();
```

Redis ZSet의 score 형식에 맞춰 이 값은 `double`로 전달된다. 조회할 때 현재 시각도 같은 방식으로 바꿔 `score <= 현재 시각`인 member를 오래된 순서로 최대 100개 가져온다. 같은 `groupBuyId`를 다시 등록하면 ZSet member가 중복되지 않고 score만 갱신된다.

현재 Outbox Publisher는 1초, 판정 Scheduler는 10초의 fixed delay 설정을 사용한다. fixed delay는 이전 실행이 끝난 후 설정된 시간이 지나면 다음 실행을 시작한다.

판정 시 DB에서는 `OPEN` 상태이고 종료 시각이 지난 공동구매 행을 비관적 쓰기 잠금으로 조회한다. 참여 인원이 목표 이상이면 모집 완료, 미달이면 실패로 상태를 변경한다. 다른 워커가 이미 판정했거나 대상이 삭제되어 `GROUPBUY_NOT_FOUND`가 발생하면 오래된 Redis 예약을 제거한다. 일시적 DB 오류 등 다른 실패에서는 member를 남겨 다음 주기에 다시 시도한다.

현재 소스는 대화 초반의 TODO 상태에서 더 진행됐다. 모집 성공이면 판정 트랜잭션이 반환되어 커밋된 후 `GroupPaymentReservationService.scheduleForGroup()`을 호출한다. 결제 예약 연결이 실패해도 이미 커밋된 판정을 되돌리지 않으며, 별도 복구 스케줄러가 보완하도록 로그를 남긴다.

## 21. 클래스 이름과 마이그레이션 정리

`GroupBuyJudgment` 접두사가 붙은 클래스 중 실제로 주문 생성 이벤트까지 함께 다루는 클래스가 있어 책임을 오해할 수 있다는 문제가 제기됐다. 이에 공용 Outbox 발행 클래스의 이름을 다음과 같이 변경했다.

| 이전 이름 | 변경 이름 | 이유 |
| --- | --- | --- |
| `GroupBuyJudgmentOutboxPublisher` | `GroupBuyOutboxPublisher` | 판정 이벤트와 주문 생성 이벤트를 모두 주기적으로 발행 |
| `GroupBuyJudgmentOutboxPublishService` | `GroupBuyOutboxPublishService` | 두 이벤트 유형을 분기해 서로 다른 Redis 자료구조에 발행 |

반면 아래 클래스는 실제 판정 전용이므로 이름을 유지했다.

- `GroupBuyJudgmentSchedule`: Redis ZSet 판정 예약 저장소
- `GroupBuyJudgmentScheduler`: 도래한 판정 실행
- `GroupBuyJudgmentService`: DB 공동구매 성공·실패 판정

관련 프로덕션 코드와 단위 테스트의 클래스명·참조를 함께 변경했으며 당시 관련 테스트는 성공했다.

Flyway 마이그레이션에는 `V12`가 두 개 존재했다. 기존 순서가 `V12__add_reject_history.sql`, `V13__product_award_evaluation_sequence.sql`이므로 주문 생성 Outbox 이벤트 타입을 추가하는 파일은 다음 빈 번호로 변경했다.

```text
V12__add_group_buy_order_creation_outbox_event.sql
  → V14__add_group_buy_order_creation_outbox_event.sql
```

SQL 내용은 변경하지 않았고, 현재 마이그레이션 목록에서 해당 구간은 V11 → V12 → V13 → V14 순서다.

## 관련 코드

- [PaymentService](../src/main/java/com/moongcheap_backend/payments/application/PaymentService.java): 현재 orderId 기준 자동결제 진입점.
- [PaymentPreparationService](../src/main/java/com/moongcheap_backend/payments/application/PaymentPreparationService.java): READY 생성·재사용.
- [PaymentCompletionService](../src/main/java/com/moongcheap_backend/payments/application/PaymentCompletionService.java): 기존 결제와 주문 완료 반영.
- [Payments](../src/main/java/com/moongcheap_backend/payments/domain/Payments.java): 결제 이력과 READY/DONE 전이.
- [PaymentsStatus](../src/main/java/com/moongcheap_backend/payments/domain/enums/PaymentsStatus.java): 결제 상태 정의.
- [TossBrandPayPaymentClient](../src/main/java/com/moongcheap_backend/payments/infrastructure/TossBrandPayPaymentClient.java): 자동결제 HTTP 호출.
- [BrandPayTokenService](../src/main/java/com/moongcheap_backend/payments/application/BrandPayTokenService.java): 토큰 발급·갱신·사용.
- [BrandPayIdempotencyKeyGenerator](../src/main/java/com/moongcheap_backend/payments/application/BrandPayIdempotencyKeyGenerator.java): 요청별 멱등키 생성.
- [PaymentController](../src/main/java/com/moongcheap_backend/payments/presentation/PaymentController.java): 인증·동기화 및 결제수단 API.
- [GroupBuyOrderCreationStream](../src/main/java/com/moongcheap_backend/groupbuy/infrastructure/GroupBuyOrderCreationStream.java): 주문 생성 Stream 발행, Pending 회수, ACK 및 Consumer Group 복구.
- [GroupBuyOrderCreationConsumer](../src/main/java/com/moongcheap_backend/order/application/GroupBuyOrderCreationConsumer.java): 오래된 Pending과 신규 주문 생성 메시지 처리.
- [GroupBuyOutboxPublisher](../src/main/java/com/moongcheap_backend/groupbuy/application/GroupBuyOutboxPublisher.java): 공동구매 Outbox 주기 실행 진입점.
- [GroupBuyOutboxPublishService](../src/main/java/com/moongcheap_backend/groupbuy/application/GroupBuyOutboxPublishService.java): 이벤트 유형별 Stream·Sorted Set 발행.
- [GroupBuyJudgmentSchedule](../src/main/java/com/moongcheap_backend/groupbuy/infrastructure/GroupBuyJudgmentSchedule.java): 판정 예정 시각을 Epoch 밀리초 score로 관리.
- [GroupBuyJudgmentScheduler](../src/main/java/com/moongcheap_backend/groupbuy/application/GroupBuyJudgmentScheduler.java): 도래한 공동구매 판정 및 결제 예약 연결.
- [GroupBuyJudgmentService](../src/main/java/com/moongcheap_backend/groupbuy/application/GroupBuyJudgmentService.java): 공동구매 성공·실패 판정 트랜잭션.
- [V14 Outbox event migration](../src/main/resources/db/migration/V14__add_group_buy_order_creation_outbox_event.sql): 주문 생성 Outbox 이벤트 타입 허용.
