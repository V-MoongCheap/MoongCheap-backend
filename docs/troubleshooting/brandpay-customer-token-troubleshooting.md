# BrandPay 결제수단 등록 `customerToken` 오류 해결 기록

## 1. 문제 상황

BrandPay JavaScript SDK의 `addPaymentMethod()`로 카드 또는 계좌를 등록할 때 다음 오류가 발생했다.

```text
customerToken이 존재하지 않습니다.
```

카드와 계좌에서 동일한 오류가 발생했으며, 결제수단은 서버 DB에 저장되지 않았다.

로컬 테스트 구성은 다음과 같았다.

- 테스트 프론트: `http://localhost:3000`
- 백엔드: `http://localhost:8080`
- BrandPay 리다이렉트 URL:

```text
http://localhost:8080/api/dev/brandpay-test/callback
```

- 테스트 프론트 서버: `python3 -m http.server 3000 --directory tools/brandpay-test`

## 2. 정상적으로 기대한 흐름

```text
addPaymentMethod() 호출
→ BrandPay 결제수단 입력 및 인증
→ SDK가 redirectUrl에 code와 customerKey 전달
→ 백엔드가 Authorization Code를 Access/Refresh Token으로 교환
→ SDK가 customerToken을 확인
→ 결제수단 등록 완료
→ 백엔드가 토스 결제수단을 조회하여 로컬 DB와 동기화
```

`code`는 일회성 Authorization Code이고, `customerKey`는 서비스 회원과 BrandPay 고객을 연결하는 식별자다. 두 값을 로그나 문서에 원문으로 남기지 않는다.

## 3. 처음 검토한 가설

### 3.1 Redis 또는 세션 문제

콜백이 인증된 회원을 찾지 못하면 토큰 발급이 실행되지 않을 수 있으므로 Redis 세션과 쿠키 설정을 확인했다.

- PostgreSQL과 Redis는 정상 실행 중이었다.
- Redis에 테스트 회원의 세션이 존재했다.
- 로컬 프로필의 세션 쿠키는 `secure: false`였다.
- `dev` 프로필도 HTTP localhost 테스트가 가능하도록 `SESSION_COOKIE_SECURE`의 기본값을 `false`로 조정했다.

그러나 이후 콜백 요청이 실제 회원 세션으로 처리된 것이 확인되어 직접적인 원인에서 제외했다.

### 3.2 리다이렉트 URL 불일치

상점관리자 등록값, SDK 초기화 값, 백엔드 매핑을 문자열 단위로 비교했다.

```text
상점관리자: http://localhost:8080/api/dev/brandpay-test/callback
SDK 전달값: http://localhost:8080/api/dev/brandpay-test/callback
백엔드 매핑: /api/dev/brandpay-test/callback
```

호스트, 포트, 경로 및 끝 슬래시까지 일치했다. 브라우저 `localStorage`에 다른 백엔드 주소가 남아 있을 가능성도 확인했지만, 테스트 화면에 표시된 실제 URL 역시 위 값과 같았다.

### 3.3 BrandPay MID 또는 API 키 권한 문제

SDK의 `customerToken` 발급 권한이나 BrandPay MID 연결 문제도 검토했다. 하지만 이것만으로 결론 내리기 전에 실제 콜백과 토큰 저장 결과를 확인했다.

DB에는 일부 테스트 고객의 Access Token과 Refresh Token이 저장되어 있었다. 런타임에는 Mock 구현이 없고, 토큰 응답의 필수 값이 모두 존재할 때만 저장하도록 구현되어 있으므로 다음 사실을 확인할 수 있었다.

```text
redirectUrl로 code/customerKey 수신
→ POST /v1/brandpay/authorizations/access-token 성공
→ Access/Refresh Token 저장 성공
```

따라서 적어도 해당 요청에서는 리다이렉트 수신과 토큰 발급이 정상적으로 수행됐다.

## 4. 결정적인 네트워크 증거

브라우저 개발자도구에서 다음 요청이 연속으로 확인됐다.

```text
OPTIONS http://localhost:8080/api/dev/brandpay-test/callback?... → 200
GET     http://localhost:8080/api/dev/brandpay-test/callback?... → 302
OPTIONS http://localhost:3000/?brandpay=success                 → 501
```

SDK 로그에도 같은 시각과 Trace ID로 다음 오류가 남았다.

```text
Redirect url failed
NetworkError: 네트워크 요청에 실패했습니다.
GET_PARAMETER customerToken
KnownError: customerToken이 존재하지 않습니다.
```

이 로그로 `customerToken` 오류가 최초 원인이 아니라, 리다이렉트 처리 실패 이후에 발생한 후속 오류임을 확인했다.

## 5. 근본 원인

로컬 백엔드 콜백은 토큰 발급과 결제수단 동기화를 수행한 뒤 다음 코드로 프론트에 다시 리다이렉트하고 있었다.

```java
response.sendRedirect(frontendBaseUrl + "/?brandpay=success");
```

SDK는 백엔드 콜백을 교차 출처 HTTP 요청으로 실행했다. 백엔드가 `302 Found`를 반환하자 SDK의 요청이 `http://localhost:3000/?brandpay=success`까지 이어졌고, 브라우저는 프론트 서버에 CORS preflight인 `OPTIONS` 요청을 보냈다.

로컬 정적 서버로 사용한 Python `SimpleHTTPRequestHandler`는 `OPTIONS` 메서드를 지원하지 않으므로 다음 응답을 반환했다.

```text
501 Unsupported method ('OPTIONS')
```

결과적으로 SDK는 백엔드에서 Access Token이 이미 발급됐음에도 전체 리다이렉트 처리를 실패로 판단했다. 이후 SDK 내부의 `customerToken` 전달 단계가 완료되지 않아 최종적으로 `customerToken이 존재하지 않습니다` 오류가 발생했다.

전체 인과관계는 다음과 같다.

```text
백엔드 콜백 성공
→ Access/Refresh Token 발급 및 저장 성공
→ 백엔드가 프론트로 302 응답
→ SDK가 302 대상에 CORS preflight 요청
→ Python 정적 서버가 OPTIONS 501 응답
→ SDK가 Redirect url failed 처리
→ SDK 내부 customerToken 설정 실패
→ 결제수단 등록 실패
```

## 6. 해결 방법

백엔드 콜백이 프론트 서버로 다시 리다이렉트하지 않도록 변경했다. 토큰 발급과 결제수단 동기화를 완료한 뒤 `204 No Content`로 요청을 종료한다.

```java
@GetMapping("/callback")
public ResponseEntity<Void> callback(
    SessionPrincipal principal,
    @RequestParam String code,
    @RequestParam String customerKey
) {
    brandPayTokenService.issue(
        principal.memberId(),
        new BrandPayAuthorizationRequest(customerKey, code)
    );
    createPayMethodService.synchronize(principal.memberId());
    return ResponseEntity.noContent().build();
}
```

프론트에서는 더 이상 사용하지 않는 `?brandpay=success` 처리도 제거했다. 기존 화면에서 실행 중이던 `addPaymentMethod()` Promise가 콜백 성공 이후 계속 진행되고, 완료되면 결제수단 동기화 및 목록 조회를 수행한다.

## 7. 수정 후 정상 흐름

```text
OPTIONS /api/dev/brandpay-test/callback → 200
GET     /api/dev/brandpay-test/callback → 204
addPaymentMethod() Promise 완료
POST    /api/payments/brandpay/methods/synchronize
GET     /api/payments/methods
```

다음 요청은 더 이상 발생하지 않아야 한다.

```text
OPTIONS http://localhost:3000/?brandpay=success
```

백엔드 재시작 후 카드와 계좌 결제수단 등록이 모두 정상적으로 완료되는 것을 확인했다.

## 8. 검증 내용

- Java 컴파일 성공
- 결제 관련 테스트 성공
- 테스트 프론트 JavaScript 문법 검사 성공
- 백엔드 콜백의 최종 응답이 `302`에서 `204`로 변경됨
- Python 정적 서버에 대한 불필요한 `OPTIONS` 요청 제거
- BrandPay 결제수단 등록 성공 확인

## 9. 문제 해결 과정에서 얻은 기준

### `customerToken` 오류만 보고 권한 문제로 단정하지 않는다

SDK가 표시하는 최종 오류는 앞 단계 실패의 결과일 수 있다. 동일한 Trace ID와 시간대의 첫 번째 오류부터 확인해야 한다. 이번 장애의 최초 오류는 `customerToken`이 아니라 `Redirect url failed`였다.

### HTTP 상태 코드만 보지 않고 리다이렉트 체인을 확인한다

백엔드 콜백의 `302` 자체는 성공처럼 보였지만, SDK는 이어지는 리다이렉트 대상까지 하나의 요청으로 처리했다. 최종 목적지의 `OPTIONS 501`이 전체 요청을 실패시켰다.

### DB 상태와 브라우저 네트워크 로그를 함께 확인한다

DB에 토큰이 저장된 사실은 토큰 발급 API 성공을 증명했지만, SDK 전체 흐름의 성공을 의미하지는 않았다. 반대로 브라우저 로그만 보면 백엔드 토큰 발급까지 실패한 것으로 오해할 수 있었다. 두 증거를 함께 확인해야 실패 경계를 정확히 찾을 수 있다.

### 콜백은 필요한 처리만 하고 단순한 성공 응답을 반환한다

SDK가 호출하는 인증 콜백에서 다른 오리진으로 추가 리다이렉트를 만들면 CORS와 정적 서버 기능에 의존하게 된다. 현재 로컬 테스트 구조에서는 콜백이 `204`로 종료되는 편이 단순하고 안정적이다.

## 10. 재발 방지 체크리스트

- [ ] 상점관리자와 SDK의 `redirectUrl`이 완전히 같은가?
- [ ] 콜백의 `OPTIONS`와 실제 요청이 모두 성공하는가?
- [ ] 콜백 이후 불필요한 교차 출처 `302`가 발생하지 않는가?
- [ ] SDK 로그에서 동일 Trace ID의 최초 오류를 확인했는가?
- [ ] Authorization Code가 Access Token으로 정상 교환됐는가?
- [ ] SDK 완료 후 토스 결제수단 조회 결과를 DB에 동기화했는가?
- [ ] 테스트 후 `code`, Access Token, Refresh Token 및 전체 `customerKey`를 로그에서 제거했는가?
- [ ] 소스 변경 후 실행 중인 백엔드를 완전히 재시작했는가?

