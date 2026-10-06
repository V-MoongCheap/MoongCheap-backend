# BrandPay 테스트 케이스

## 1. 문서 목적

BrandPay 결제수단 등록부터 자동결제까지 현재 구현된 테스트 범위와 실행 방법을
정리한다. 테스트는 다음 두 종류로 구분한다.

- **수동 연동 테스트**: 로컬 테스트 콘솔과 토스페이먼츠 테스트 환경을 사용한다.
- **자동화 테스트**: 토스 HTTP 응답과 저장소를 대역으로 사용해 애플리케이션 로직을 검증한다.

수동 테스트 콘솔과 전용 API는 `local`, `dev` 프로필에서만 활성화된다.

## 2. 테스트 환경 준비

### 2.1 백엔드와 인프라 실행

저장소 루트에서 PostgreSQL과 Redis를 실행한 뒤 백엔드를 시작한다.

```bash
docker compose -f docker/docker-compose.local.yml up -d postgres redis
./gradlew bootRun
```

### 2.2 테스트 콘솔 실행

다른 터미널에서 다음 명령을 실행한다.

```bash
python3 -m http.server 3000 --directory tools/brandpay-test
```

브라우저에서 <http://localhost:3000>에 접속한다.

토스페이먼츠 개발자센터의 BrandPay 리다이렉트 URL은 다음 값과 정확히 일치해야 한다.

```text
http://localhost:8080/api/dev/brandpay-test/callback
```

실제 키 값은 문서나 프론트 코드에 기록하지 않는다. 클라이언트 키만 백엔드 설정을
통해 프론트에 전달하며 시크릿 키와 보안 키는 서버 밖으로 노출하지 않는다.

## 3. 수동 연동 테스트

### BP-M01. 테스트 사용자 세션 생성

**절차**

1. 테스트 콘솔에서 `테스트 사용자로 계속`을 누른다.
2. 연결 완료 후 화면에 표시된 `customerKey`와 클라이언트 키를 확인한다.

**기대 결과**

- 테스트 회원이 없으면 생성되고, 있으면 기존 회원을 재사용한다.
- 약관 동의가 완료된 구매자 세션이 생성된다.
- BrandPay SDK 초기화가 완료되고 결제수단 등록 버튼이 활성화된다.

### BP-M02. 카드 결제수단 최초 등록

**사전 조건**

- BP-M01이 성공해야 한다.
- 해당 `customerKey`에 유효한 BrandPay 토큰이 아직 없어도 된다.

**절차**

1. 결제수단 종류로 카드를 선택한다.
2. `결제수단 추가`를 눌러 토스 BrandPay 화면에서 카드 등록을 완료한다.
3. 브라우저 네트워크 탭과 테스트 콘솔 로그를 확인한다.

**기대 결과**

- SDK가 다음 콜백을 호출한다.

  ```text
  GET /api/dev/brandpay-test/callback?code={authorizationCode}&customerKey={customerKey}
  ```

- 백엔드는 일회성 인증 코드를 Access Token과 Refresh Token으로 교환해 암호화 저장한다.
- 이어서 토스 결제수단 전체 목록을 조회하고 로컬 DB에 동기화한다.
- 콜백은 리다이렉트 없이 `204 No Content`를 반환한다.
- 등록된 카드가 테스트 콘솔의 결제수단 목록에 나타난다.

### BP-M03. 계좌 결제수단 최초 등록

BP-M02와 동일한 절차를 계좌로 수행한다.

**기대 결과**

- 계좌 등록, 토큰 발급 및 결제수단 동기화가 완료된다.
- 등록된 계좌가 결제수단 목록에 나타난다.
- 카드와 계좌가 함께 존재하면 두 종류 모두 조회된다.

### BP-M04. 기존 사용자 결제수단 추가 및 수동 동기화

**절차**

1. 이미 토큰을 발급받은 테스트 사용자로 연결한다.
2. 결제수단을 추가하거나 토스 쪽 결제수단 상태를 변경한다.
3. `토스에서 다시 동기화`를 누른다.

**기대 결과**

- Access Token이 유효하면 기존 토큰을 사용한다.
- Access Token이 만료되었거나 만료까지 1분 이내라면 Refresh Token으로 갱신한다.
- `methodKey`가 같은 수단은 갱신되고 새 수단은 생성된다.
- 토스 응답에서 사라진 로컬 수단은 `EXPIRED`로 변경된다.
- 사용자 목록 응답에서는 `EXPIRED` 수단이 제외된다.

### BP-M05. 중간 인증 상태에서 새 고객으로 재시도

**사전 조건**

- 이전 인증 실패로 `customerToken이 존재하지 않습니다` 오류가 반복되는 상태다.

**절차**

1. 테스트 콘솔에서 `새 테스트 고객으로 시작`을 누른다.
2. 새 `customerKey`가 표시되는지 확인한다.
3. 카드 또는 계좌 등록을 다시 수행한다.

**기대 결과**

- 새 회원 세션과 새 `customerKey`가 발급된다.
- 이전 고객의 중간 인증 상태와 분리되어 결제수단을 등록할 수 있다.

### BP-M06. `PAYMENT_PENDING` 테스트 주문 생성

**사전 조건**

- 로그인 회원에게 `ACTIVE` 상태의 BrandPay 결제수단이 하나 이상 있어야 한다.

**절차**

1. 결제수단 목록에서 사용할 수단을 선택한다.
2. 주문명과 100원 이상 1,000,000원 이하의 금액을 입력한다.
3. `대기 주문 생성`을 누른다.

**기대 결과**

- 자동결제 조건을 갖춘 판매자, 상품, 공동구매, 수요 및 주문 데이터가 한 트랜잭션에서 생성된다.
- 주문 상태는 `PAYMENT_PENDING`이다.
- 응답의 주문 ID가 자동결제 실행 입력란에 자동으로 설정된다.

### BP-M07. BrandPay 자동결제 성공

**사전 조건**

- BP-M06으로 생성한 주문이 있어야 한다.
- 선택한 결제수단이 실제 토스 테스트 환경에서 자동결제 가능한 상태여야 한다.

**절차**

1. 생성된 주문 ID를 확인한다.
2. `자동결제 실행`을 누른다.
3. 화면에 표시되는 Payment ID, 주문번호, 금액, 상태와 시도 횟수를 확인한다.

**기대 결과**

- 토스 요청 전에 `Payments`와 Outbox가 먼저 저장된다.
- 테스트 API는 운영 `PaymentWorker`와 같은 실행 경로를 한 번 동기적으로 수행한다.
- 토스 자동결제 요청에는 주문 기반 멱등키가 사용된다.
- 승인 성공 시 결제 상태는 `SUCCEEDED`, 주문 상태는 `PAYMENT_COMPLETED`가 된다.
- 결제 완료 Outbox가 생성된다.

승인 결과가 `FAILED`, `UNKNOWN`, `REVIEW_REQUIRED`라면 성공으로 간주하지 않고 서버
로그, 토스 API 모니터링과 응답 오류 코드를 함께 확인한다.

### BP-M08. 입력 및 소유권 오류

다음 입력은 거부되어야 한다.

| 조건 | 기대 결과 |
| --- | --- |
| 결제수단을 선택하지 않고 주문 생성 | 프론트 입력 오류 |
| 금액이 100원 미만 또는 1,000,000원 초과 | 프론트 또는 백엔드 유효성 오류 |
| 빈 주문명 또는 100자를 초과한 주문명 | 프론트 또는 백엔드 유효성 오류 |
| 존재하지 않는 주문 ID로 결제 | 주문 없음 오류 |
| 다른 회원 소유 주문으로 결제 | 권한 없음 오류 |
| 다른 회원 또는 비활성 결제수단으로 주문 생성 | 결제수단 없음 오류 |

## 4. 자동화 테스트

결제 모듈 자동화 테스트는 다음 명령으로 실행한다.

```bash
./gradlew test --tests '*payments*' --no-daemon
```

전체 프로젝트 회귀 테스트는 다음 명령으로 실행한다.

```bash
./gradlew test --no-daemon
```

### 4.1 멱등키 생성

`BrandPayIdempotencyKeyGeneratorUnitTest`

- 같은 회원과 암호화 Access Token은 같은 갱신 멱등키를 만든다.
- 회원 또는 암호화 Access Token이 달라지면 갱신 멱등키도 달라진다.
- 최초 발급과 갱신은 입력이 같아도 서로 다른 멱등키를 만든다.
- 같은 결제수단 삭제 요청은 같은 멱등키를 만든다.
- 같은 주문번호의 자동결제는 같은 멱등키를 만든다.

### 4.2 토큰 발급과 갱신

`BrandPayTokenServiceUnitTest`

- SDK 인증 코드를 토큰으로 교환하고 암호화해 저장한다.
- 기존 토큰이 있으면 새 토큰 값으로 갱신한다.
- 로그인 회원의 `customerKey`와 요청 값이 다르면 토큰 API를 호출하지 않는다.
- 저장된 Refresh Token을 복호화해 새 토큰을 발급받는다.
- 저장된 토큰이 없으면 갱신을 거부한다.
- Access Token이 유효하면 복호화한 기존 토큰을 반환한다.
- Access Token이 만료되면 갱신된 토큰을 반환한다.
- 만료까지 1분 이내인 Access Token도 미리 갱신한다.

`TossBrandPayAuthorizationClientUnitTest`

- 시크릿 키 Basic 인증과 Authorization Code로 토큰 발급 요청을 만든다.
- Refresh Token으로 새 인증 토큰 발급 요청을 만든다.

### 4.3 결제수단 조회, 동기화 및 삭제

`CreatePayMethodServiceUnitTest`

- 유효한 Access Token으로 토스 결제수단을 조회하고 동기화 서비스에 전달한다.

`BrandPayMethodSyncServiceUnitTest`

- 토스 전체 응답을 `methodKey` 기준으로 등록·갱신하고 누락된 수단을 만료시킨다.

`PaymentServiceUnitTest`

- `EXPIRED` 수단을 제외하고 기본 결제수단부터 조회한다.
- 회원의 결제수단을 토스에서 삭제하고 로컬 상태를 `EXPIRED`로 바꾼다.
- 자동결제 진입점은 외부 청구를 직접 실행하지 않고 예약만 생성한다.
- 승인된 회원 결제를 전액 취소하고 주문을 환불 완료로 바꾼다.
- 이미 취소된 결제는 토스 취소 API를 다시 호출하지 않는다.

`TossBrandPayMethodClientUnitTest`

- Access Token으로 등록된 카드와 계좌를 모두 조회한다.
- 카드 결제수단을 `methodKey`로 삭제한다.
- 계좌 결제수단을 `methodKey`로 삭제한다.

### 4.4 결제 예약과 Outbox

`PaymentPreparationServiceUnitTest`

- 결제와 Outbox를 같은 예약 트랜잭션에서 생성한다.
- 기존 결제가 있으면 새 결제로 대체하지 않는다.

`PaymentOutboxPublishServiceUnitTest`

- 실행 가능한 결제를 Redis Sorted Set에 발행하고 Outbox를 완료 처리한다.

`GroupPaymentReservationServiceUnitTest`

- 큐가 활성화되면 성사된 공동구매 주문을 결제 예약한다.
- 큐가 비활성화되면 주문을 조회하거나 예약하지 않는다.

### 4.5 워커와 실행 상태 제어

`PaymentExecutionServiceUnitTest`

- 실행 시작 시 소유권 토큰을 얻고 결제를 `UNKNOWN` 상태로 커밋한다.
- 오래된 처리 토큰으로 도착한 성공 결과는 반영하지 않는다.
- 현재 처리 토큰의 성공 결과는 결제와 주문에 함께 반영하고 Outbox를 생성한다.

`PaymentWorkerUnitTest`

- 실행할 후보가 없으면 DB와 토스 클라이언트를 호출하지 않는다.

`PaymentWorkerPoolUnitTest`

- 기본 설정에서 워커 슬롯 하나만 실행하고 별도 대기열을 쌓지 않는다.

`PaymentScheduleRedisTest`

- 두 소비자가 동시에 같은 결제를 획득하려 해도 한 소비자만 후보를 받는다.

이 테스트는 기본 실행에서는 환경변수가 없어 건너뛴다. 실제 Redis로 실행하려면 다음과
같이 접속 정보를 전달한다.

```bash
PAYMENT_QUEUE_REDIS_HOST=localhost \
PAYMENT_QUEUE_REDIS_PORT=6379 \
./gradlew test --tests '*PaymentScheduleRedisTest' --no-daemon
```

### 4.6 토스 자동결제와 취소 HTTP 요청

`TossBrandPayPaymentClientUnitTest`

- Basic 인증과 멱등키를 사용해 전액 취소 요청을 만든다.
- 명시적인 `NOT_FOUND_PAYMENT`만 결제 미조회로 처리한다.
- 승인 거절 응답만 확정 실패로 분류한다.
- 카드 자동결제 요청에 Basic 인증, 멱등키 및 카드 결제 정보를 포함한다.
- 계좌 자동결제 요청에 문화비 여부를 포함한다.

### 4.7 로컬 테스트 결제 실행

`DevBrandPayTestPaymentServiceUnitTest`

- 로그인 회원의 주문은 결제를 먼저 예약한 뒤 기존 워커로 즉시 실행한다.
- 다른 회원 소유 주문은 실행하지 않는다.

## 5. 현재 자동화되지 않은 범위

다음 항목은 현재 수동 테스트로 확인하며 별도의 자동화 테스트가 없다.

- `DevBrandPayTestOrderService`가 전체 연관 데이터를 만들고 주문을
  `PAYMENT_PENDING`으로 저장하는 통합 테스트
- 개발용 callback이 토큰 발급과 결제수단 동기화 후 `204 No Content`를 반환하는 통합 테스트
- 브라우저에서 실제 BrandPay SDK 화면을 통한 카드·계좌 등록 E2E 테스트
- 토스 테스트 환경을 실제 호출하는 자동결제 및 취소 E2E 테스트
- 재시도 횟수별 지연, `UNKNOWN` 조회 조정, 최대 시도 초과 후
  `REVIEW_REQUIRED` 전환을 함께 검증하는 통합 테스트

외부 토스 테스트 환경에 의존하는 E2E 테스트는 일반 CI 테스트와 분리해 수동 또는
별도 프로필에서 실행하는 것이 적절하다.

## 6. 결과 기록 양식

수동 테스트 결과는 다음 양식으로 남긴다. 인증 코드, Access Token, Refresh Token,
`methodKey`, 시크릿 키와 보안 키는 기록하지 않는다.

```text
테스트 일시:
테스트 케이스 ID:
실행 환경/프로필:
결제수단 종류:
주문 ID:
Payment ID:
최종 주문 상태:
최종 결제 상태:
토스 오류 코드(실패 시):
결과: PASS / FAIL
비고:
```
