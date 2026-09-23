# BrandPay 결제수단 등록 테스트 프론트

## 실행

저장소 루트에서 PostgreSQL과 Redis를 실행하고 백엔드를 시작한다.

```bash
docker compose -f docker/docker-compose.local.yml up -d postgres redis
./gradlew bootRun
```

다른 터미널에서 테스트 프론트를 실행한다.

```bash
python3 -m http.server 3000 --directory tools/brandpay-test
```

브라우저에서 <http://localhost:3000>을 연다.

결제 API에는 로그인 세션이 필요하다. `local` 또는 `dev` 프로필에서는 화면의
`테스트 사용자로 계속` 버튼으로 테스트 회원과 세션을 자동 생성할 수 있다. 이 API는
운영 프로필에서는 등록되지 않는다. 기존 로그인 세션이나 OAuth 로그인을 사용해도 된다.

## 토스페이먼츠 개발자센터 설정

브랜드페이 리다이렉트 URL에 아래 백엔드 콜백 주소를 정확히 등록한다.

```text
http://localhost:8080/api/dev/brandpay-test/callback
```

## 흐름

1. OAuth 로그인 세션을 만든다.
2. 백엔드에서 `clientKey`, 회원별 `customerKey`를 조회한다.
3. `TossPayments(clientKey).brandpay(...)`로 SDK를 초기화한다.
4. `addPaymentMethod()`로 카드 또는 계좌를 등록한다.
5. 최초 인증 리다이렉트의 `code`, `customerKey`를 백엔드에 전달한다.
6. 백엔드가 Access Token을 발급하고 토스 결제수단을 DB에 동기화한다.
7. 서버에 저장된 결제수단 목록을 화면에 표시한다.
8. 활성 결제수단을 선택해 `PAYMENT_PENDING` 테스트 주문을 생성한다.
9. 생성된 주문 ID로 자동결제를 즉시 실행한다.

자동결제 테스트는 Toss API를 직접 우회 호출하지 않는다. 먼저 기존 서비스로
`Payments`와 Outbox를 생성한 다음 운영 `PaymentWorker`와 동일한 실행 경로를 한 번
동기적으로 수행한다. 주문은 공동구매 판정 완료, `PAYMENT_PENDING`, 로그인 회원 소유,
활성 BrandPay 결제수단 지정 조건을 만족해야 한다.

백엔드 콜백은 처리를 마치면 `204 No Content`를 반환한다. 정적 프론트 서버로 다시
HTTP 리다이렉트하지 않으므로 `python -m http.server`가 지원하지 않는 `OPTIONS`
요청으로 이어지지 않고, 기존 화면에서 실행 중인 `addPaymentMethod()`가 계속 진행된다.

`customerToken이 존재하지 않습니다`처럼 이전 인증 실패로 특정 customerKey가 중간
상태에 걸리면 화면의 `새 테스트 고객으로 시작` 버튼으로 새로운 회원/customerKey를
발급한 뒤 다시 시도한다.

시크릿 키와 보안 키는 브라우저 코드 또는 응답에 포함하지 않는다.
