# 인증·인가 정책 및 권한 매트릭스

> 기준 코드: `src/main/java/com/moongcheap_backend/auth/infrastructure/`

---

## 1. 필터 체인 구조

요청은 아래 순서로 필터를 통과한다.

```
HTTP 요청
   │
   ▼
┌──────────────────────┐
│  InternalApiKeyFilter │  /api/**/internal, /api/awarding/**
│  (API Key 인증)       │  X-Internal-Api-Key 헤더 검증
└──────────┬───────────┘
           │ 해당 없으면 통과
           ▼
┌───────────────────────────┐
│  SessionAuthenticationFilter│  모든 요청
│  (세션 → SecurityContext)   │  세션의 SessionPrincipal → Authentication 주입
└──────────┬────────────────┘
           │
           ▼
┌───────────────────────────┐
│  IncompleteSignupFilter    │  인증된 사용자 중 termsAgreed=false 차단
│  (소셜 가입 미완료 차단)     │
└──────────┬────────────────┘
           │
           ▼
┌───────────────────────────┐
│  Spring Security           │  SecurityConfig.authorizeHttpRequests()
│  (permitAll / authenticated)│
└───────────────────────────┘
```

---

## 2. 인증 방식

### 2.1 로컬 로그인

| 단계     | 내용                                                              |
|--------|-----------------------------------------------------------------|
| 엔드포인트  | `POST /api/auth/login`                                          |
| 검증 순서  | ① Login ID 정규화 → ② 잠금 여부 확인 → ③ Member 조회 → ④ 비밀번호 검증 → ⑤ 세션 발급 |
| 실패 카운터 | Redis, Key: `moongcheap:login-fail:{loginId}`                   |
| 잠금 임계치 | 연속 5회 실패 → 10분 잠금 (`AUTH_007 LOGIN_LOCKED`)                     |
| 성공 시   | 실패 카운터 초기화 + `SessionPrincipal` 세션 바인딩                          |

### 2.2 소셜 로그인 (OAuth2)

지원 프로바이더: **Kakao**, **Google**

```
/oauth2/authorization/{provider}
           │
           ▼
  CustomOAuth2UserService
  ├─ 로그인 상태 → 기존 회원에 소셜 계정 연동
  └─ 비로그인 상태
       ├─ 기존 소셜 계정 → Member 조회
       └─ 신규 → Member 생성 (termsAgreed=false, 닉네임 자동 할당)
           │
           ▼
  OAuth2LoginSuccessHandler
  ├─ SessionPrincipal 세션 바인딩 (rememberMe=false)
  ├─ Google: AccessToken을 세션에 저장
  ├─ termsAgreed=true  → redirect: /oauth/callback
  └─ termsAgreed=false → redirect: /oauth/callback?status=incomplete
```

소셜 가입 완료 흐름:

```
POST /api/auth/social-signup/complete
  → 약관 동의 처리 (중복 호출 불가)
  → 닉네임 변경 (선택)
  → SessionPrincipal 갱신
```

### 2.3 내부 API 인증 (서비스 간 통신)

| 항목     | 내용                                                            |
|--------|---------------------------------------------------------------|
| 헤더     | `X-Internal-Api-Key: {key}`                                   |
| 대상 패턴  | `/api/**/internal`, `/api/**/internal/**`, `/api/awarding/**` |
| 설정 키   | `moongcheap.security.internal-api-key`                        |
| 실패 응답  | `HTTP 401` + `COMMON_401`                                     |
| 키 공백 시 | 항상 `HTTP 401` (설정 미완료 보호)                                     |

---

## 3. 세션 정책

| 항목              | 값                                      |
|-----------------|----------------------------------------|
| 저장소             | Redis (`moongcheap:session` namespace) |
| 쿠키 이름           | `SID`                                  |
| HttpOnly        | `true`                                 |
| Secure          | `true` (prod) / `false` (local)        |
| SameSite        | `Lax`                                  |
| 기본 TTL          | 24시간                                   |
| Remember-Me TTL | 14일                                    |
| 생성 정책           | `IF_REQUIRED`                          |
| 세션 키 속성명        | `MOONGCHEAP_PRINCIPAL`                 |

세션 무효화 시나리오:

| 시나리오    | 동작                        |
|---------|---------------------------|
| 로그아웃    | 현재 세션만 무효화                |
| 비밀번호 변경 | 현재 세션 유지, 다른 기기 세션 전체 무효화 |
| 회원 탈퇴   | 해당 회원의 모든 세션 무효화          |
| 재로그인    | 기존 세션 무효화 후 새 세션 발급       |

---

## 4. 역할(Role) 정의

```
MemberRole
 ├─ BUYER   : 모든 회원에게 기본 부여
 └─ SELLER  : 판매자 등록 완료 시 추가 부여
```

역할은 `SessionPrincipal.roles()` 에 저장되며, SecurityContext에서 `ROLE_BUYER` / `ROLE_SELLER` 형태로 노출된다.
현재 엔드포인트 레벨 역할 검증(`@PreAuthorize`)은 미사용이며, 역할 구분은 비즈니스 로직에서 처리한다.

추가 상태값:

| 필드               | 의미                        |
|------------------|---------------------------|
| `sellerApproved` | 판매자 승인 여부 (Seller 엔티티 기반) |
| `termsAgreed`    | 소셜 가입 약관 동의 완료 여부         |

---

## 5. 소셜 가입 미완료 차단 정책 (IncompleteSignupFilter)

`termsAgreed=false` 인 인증 사용자가 허용 경로 외 접근 시 차단.

**허용 경로 (whitelist)**:

| 경로                                        | 목적        |
|-------------------------------------------|-----------|
| `POST /api/auth/social-signup/complete`   | 가입 완료 처리  |
| `POST /api/auth/logout`                   | 로그아웃      |
| `GET /api/members/nicknames/availability` | 닉네임 중복 확인 |
| `/swagger-ui/**`, `/v3/api-docs/**`       | API 문서    |

차단 응답: `HTTP 403` + `AUTH_016 SOCIAL_SIGNUP_INCOMPLETE`

---

## 6. 권한 매트릭스

### 범례

| 기호 | 의미            |
|----|---------------|
| ✅  | 허용            |
| 🔒 | 인증 필요 (세션)    |
| 🔑 | 내부 API Key 필요 |
| ❌  | 차단            |

### 6.1 인증 (Auth)

| Method     | 경로                                  | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|------------|-------------------------------------|:---:|:---------:|:-----:|:-----:|
| POST       | `/api/auth/signup`                  |  ✅  |     ✅     |   ✅   |   -   |
| POST       | `/api/auth/login`                   |  ✅  |     ✅     |   ✅   |   -   |
| GET        | `/api/auth/login-id-availability`   |  ✅  |     ✅     |   ✅   |   -   |
| POST       | `/api/auth/logout`                  |  ❌  |    🔒     |   ✅   |   -   |
| DELETE     | `/api/auth/withdraw`                |  ❌  |    🔒     |   ❌   |   -   |
| POST       | `/api/auth/social-signup/complete`  |  ❌  |    🔒     |   ✅   |   -   |
| GET/DELETE | `/api/auth/social-links/{provider}` |  ❌  |    🔒     |   ❌   |   -   |

### 6.2 회원 (Member)

| Method | 경로                                             | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|------------------------------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/members/nicknames/**`                    |  ✅  |     ✅     |   ✅   |   -   |
| GET    | `/api/members/me`                              |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/members/me`                              |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/members/me/notification-settings`        |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/members/me/notification-settings/{type}` |  ❌  |    🔒     |   ❌   |   -   |

### 6.3 배송지 (Shipping Address)

| Method | 경로                                     | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|----------------------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/shipping-addresses`              |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/shipping-addresses/{id}`         |  ❌  |    🔒     |   ❌   |   -   |
| POST   | `/api/shipping-addresses`              |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/shipping-addresses/{id}`         |  ❌  |    🔒     |   ❌   |   -   |
| DELETE | `/api/shipping-addresses/{id}`         |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/shipping-addresses/{id}/default` |  ❌  |    🔒     |   ❌   |   -   |

### 6.4 판매자 (Seller)

| Method | 경로                         | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|----------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/sellers/{id}/public` |  ✅  |     ✅     |   ✅   |   -   |
| POST   | `/api/sellers`             |  ❌  |    🔒     |   ❌   |   -   |

### 6.5 카테고리 (Category)

| Method | 경로                | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|-------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/categories` |  ✅  |     ✅     |   ✅   |   -   |

### 6.6 공동구매 (GroupBuy)

| Method | 경로                     | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/group-buys`      |  ✅  |     ✅     |   ✅   |   -   |
| GET    | `/api/group-buys/{id}` |  ✅  |     ✅     |   ✅   |   -   |

### 6.7 상품 (Product)

| Method | 경로                                   | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|--------------------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/product-catalog`               |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/product-catalog/{id}`          |  ❌  |    🔒     |   ❌   |   -   |
| POST   | `/api/products-search/internal`      |  ❌  |     ❌     |   ❌   |  🔑   |
| POST   | `/api/products-search/internal/bulk` |  ❌  |     ❌     |   ❌   |  🔑   |
| DELETE | `/api/products-search/internal/{id}` |  ❌  |     ❌     |   ❌   |  🔑   |

### 6.8 수요 (Demand)

| Method | 경로                                   | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|--------------------------------------|:---:|:---------:|:-----:|:-----:|
| POST   | `/api/members/me/demand`             |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/members/me/demand`             |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/members/me/demand/{id}`        |  ❌  |    🔒     |   ❌   |   -   |
| DELETE | `/api/members/me/demand/{id}`        |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/members/me/demand/{id}/accept` |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/members/me/demand/{id}/reject` |  ❌  |    🔒     |   ❌   |   -   |

### 6.9 수요 보드 (DemandBoard)

| Method | 경로                                                   | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|------------------------------------------------------|:---:|:---------:|:-----:|:-----:|
| POST   | `/api/demand-boards/{id}/join`                       |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/demand-boards/exists`                          |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/demand-boards`                                 |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/demand-boards/{id}`                            |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/demand-boards/{id}/auction-result`             |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/demand-boards/catalog/{catalogId}`             |  ❌  |    🔒     |   ❌   |   -   |
| POST   | `/api/demand-boards/internal/formation-plans`        |  ❌  |     ❌     |   ❌   |  🔑   |
| POST   | `/api/demand-boards/internal/substitute-offer-plans` |  ❌  |     ❌     |   ❌   |  🔑   |

### 6.10 낙찰 (Awarding)

| Method | 경로                              | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|---------------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/awarding/pending`         |  ❌  |     ❌     |   ❌   |  🔑   |
| POST   | `/api/awarding/internal/result` |  ❌  |     ❌     |   ❌   |  🔑   |

### 6.11 주문 (Order)

| Method | 경로                                       | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|------------------------------------------|:---:|:---------:|:-----:|:-----:|
| GET    | `/api/orders/list`                       |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/orders/{orderNo}`                  |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/orders/{orderNo}/cancel`           |  ❌  |    🔒     |   ❌   |   -   |
| POST   | `/api/orders/{orderNo}/shipping-address` |  ❌  |    🔒     |   ❌   |   -   |

### 6.12 결제 (Payment)

| Method | 경로                           | 비인증 | 인증(BUYER) | 가입미완료 | 내부API |
|--------|------------------------------|:---:|:---------:|:-----:|:-----:|
| POST   | `/api/payments/methods`      |  ❌  |    🔒     |   ❌   |   -   |
| GET    | `/api/payments/methods`      |  ❌  |    🔒     |   ❌   |   -   |
| DELETE | `/api/payments/methods/{id}` |  ❌  |    🔒     |   ❌   |   -   |
| PATCH  | `/api/payments/{id}`         |  ❌  |    🔒     |   ❌   |   -   |

---

## 7. 인증·인가 에러 코드

| 코드           | HTTP | 에러 코드                          | 발생 조건                              |
|--------------|------|--------------------------------|------------------------------------|
| `COMMON_401` | 401  | UNAUTHORIZED                   | 비인증 사용자가 보호된 경로 접근, 내부 API Key 불일치 |
| `COMMON_403` | 403  | FORBIDDEN                      | 인가 실패                              |
| `AUTH_006`   | 401  | LOGIN_FAILED                   | 아이디 또는 비밀번호 불일치                    |
| `AUTH_007`   | 423  | LOGIN_LOCKED                   | 연속 5회 로그인 실패 → 10분 잠금              |
| `AUTH_010`   | 400  | OAUTH_STATE_INVALID            | OAuth2 state 파라미터 검증 실패            |
| `AUTH_011`   | 409  | SOCIAL_ALREADY_LINKED          | 다른 회원에 이미 연동된 소셜 계정                |
| `AUTH_012`   | 400  | LAST_CREDENTIAL_CANNOT_UNLINK  | 마지막 로그인 수단은 해제 불가                  |
| `AUTH_013`   | 400  | SOCIAL_SIGNUP_ALREADY_COMPLETE | 소셜 가입 완료를 이미 처리한 계정                |
| `AUTH_016`   | 403  | SOCIAL_SIGNUP_INCOMPLETE       | 약관 동의 미완료 사용자의 일반 경로 접근            |
