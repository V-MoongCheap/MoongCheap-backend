# Enum 값 정리

> Status 값은 [status.md](status.md)를 참조하세요.

## SellerStatus
> `com.moongcheap_backend.member.domain.SellerStatus`
> 판매자 계정의 심사·운영 상태를 나타냅니다.

| 값 | 설명 |
|---|---|
| `PENDING` | 판매자 등록 신청 후 관리자 심사 대기 중 |
| `APPROVED` | 심사 완료, 정상 영업 가능 상태 |
| `BLOCKED` | 관리자에 의해 판매 차단된 상태 |
| `WITHDRAWN` | 판매자 스스로 탈퇴한 상태 |

**상태 전이**
```
PENDING → APPROVED   (관리자 승인)
APPROVED → BLOCKED   (관리자 차단)
APPROVED → WITHDRAWN (판매자 탈퇴 → softDelete())
```

---

## MemberRole
> **Java enum** `com.moongcheap_backend.common.security.MemberRole`
> 회원의 역할(권한)을 나타냅니다. Spring Security 인증에 사용됩니다. `Member.isSeller` 값에서 파생됩니다.

| 값 | 설명 |
|---|---|
| `BUYER` | 일반 구매자 |
| `SELLER` | 판매자 (Member.isSeller = true 인 경우) |

---

## SocialProvider
> **Java enum** `com.moongcheap_backend.member.domain.SocialProvider`
> 소셜 로그인 연동 제공자를 나타냅니다.

| 값 | 설명 |
|---|---|
| `KAKAO` | 카카오 OAuth2 |
| `GOOGLE` | 구글 OAuth2 |

---

## NotificationType
> **Java enum** `com.moongcheap_backend.notification.domain.NotificationType`
> 알림 종류를 나타냅니다. `mandatory` 필드가 `true`이면 수신 거부 불가 알림입니다.

### 구매자 알림

| 값 | mandatory | 설명 |
|---|:---:|---|
| `DEMAND_REGISTERED` | false | 수요 등록 |
| `DEMAND_MERGED` | false | 수요 병합 |
| `RECRUIT_STATUS` | false | 모집 현황 |
| `BID_RESULT` | **true** | 낙찰 결과 |
| `PAYMENT_SCHEDULED` | **true** | 결제 예정 |
| `PAYMENT_COMPLETED` | **true** | 결제 완료 |
| `PAYMENT_FAILED` | **true** | 결제 실패 |
| `DELIVERY` | **true** | 배송 |
| `CANCELED` | **true** | 무산 |

### 판매자 알림

| 값 | mandatory | 설명 |
|---|:---:|---|
| `BID_COMPETITION` | false | 응찰 경쟁 현황 |
| `BID_WON` | **true** | 낙찰 |
| `BID_LOST` | false | 미낙찰 |
| `ORDER_CREATED` | false | 주문 발생 |
| `STOCK_DEPLETED` | false | 재고 소진 |

---

## ErrorCode
> **Java enum** `com.moongcheap_backend.common.exception.ErrorCode`
> API 에러 응답에 사용되는 코드·메시지 매핑입니다.

### 공통

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `INVALID_INPUT` | 400 | COMMON_400 | 입력값이 올바르지 않습니다. |
| `UNAUTHORIZED` | 401 | COMMON_401 | 인증이 필요합니다. |
| `FORBIDDEN` | 403 | COMMON_403 | 접근 권한이 없습니다. |
| `NOT_FOUND` | 404 | COMMON_404 | 리소스를 찾을 수 없습니다. |
| `INTERNAL_ERROR` | 500 | COMMON_500 | 서버 오류가 발생했습니다. |
| `CONCURRENT_REQUEST_CONFLICT` | 409 | COMMON_409 | 요청이 충돌했습니다. 잠시 후 다시 시도해주세요. |

### Auth

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `LOGIN_ID_DUPLICATED` | 409 | AUTH_001 | 이미 사용 중인 아이디입니다. |
| `LOGIN_ID_INVALID` | 400 | AUTH_002 | 아이디 형식이 올바르지 않습니다. |
| `PASSWORD_INVALID` | 400 | AUTH_003 | 비밀번호 형식이 올바르지 않습니다. |
| `PASSWORD_MISMATCH` | 400 | AUTH_004 | 비밀번호가 일치하지 않습니다. |
| `PASSWORD_CONTAINS_LOGIN_ID` | 400 | AUTH_005 | 비밀번호에 아이디를 포함할 수 없습니다. |
| `LOGIN_FAILED` | 401 | AUTH_006 | 아이디 또는 비밀번호가 올바르지 않습니다. |
| `LOGIN_LOCKED` | 423 | AUTH_007 | 연속 로그인 실패로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요. |
| `LOCAL_CREDENTIAL_REQUIRED` | 400 | AUTH_008 | 비밀번호가 등록된 회원만 사용할 수 있습니다. |
| `PASSWORD_SAME_AS_PREVIOUS` | 400 | AUTH_009 | 이전과 동일한 비밀번호는 사용할 수 없습니다. |
| `OAUTH_STATE_INVALID` | 400 | AUTH_010 | OAuth 상태값 검증에 실패했습니다. |
| `SOCIAL_ALREADY_LINKED` | 409 | AUTH_011 | 이미 다른 회원에 연동된 소셜 계정입니다. |
| `LAST_CREDENTIAL_CANNOT_UNLINK` | 400 | AUTH_012 | 마지막 로그인 수단은 해제할 수 없습니다. |
| `WITHDRAW_BLOCKED_HAS_ORDER` | 400 | AUTH_014 | 진행 중인 거래가 있어 탈퇴할 수 없습니다. |
| `CONCURRENT_SIGNUP_CONFLICT` | 409 | AUTH_015 | 회원가입에 실패했습니다. 잠시 후 다시 시도해주세요. |

### Member

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `MEMBER_NOT_FOUND` | 404 | USER_001 | 회원을 찾을 수 없습니다. |
| `NICKNAME_DUPLICATED` | 409 | USER_002 | 이미 사용 중인 닉네임입니다. |
| `NICKNAME_INVALID` | 400 | USER_003 | 닉네임 형식이 올바르지 않습니다. |

### Shipping

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `SHIPPING_ADDRESS_NOT_FOUND` | 404 | SHIP_001 | 배송지를 찾을 수 없습니다. |
| `SHIPPING_ADDRESS_LIMIT_EXCEEDED` | 400 | SHIP_002 | 배송지는 최대 5개까지 등록할 수 있습니다. |
| `SHIPPING_ADDRESS_FORBIDDEN` | 403 | SHIP_003 | 본인 소유의 배송지가 아닙니다. |
| `SHIPPING_ADDRESS_DEFAULT_CONFLICT` | 409 | SHIP_004 | 기본 배송지 변경이 충돌했습니다. 다시 시도해주세요. |

### Seller

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `SELLER_ALREADY_REGISTERED` | 409 | SELLER_001 | 이미 판매자로 등록되어 있습니다. |
| `BUSINESS_NUMBER_INVALID` | 400 | SELLER_002 | 사업자등록번호 형식이 올바르지 않습니다. |
| `BUSINESS_NUMBER_DUPLICATED` | 409 | SELLER_003 | 이미 등록된 사업자등록번호입니다. |
| `SELLER_NOT_FOUND` | 404 | SELLER_004 | 판매자 정보를 찾을 수 없습니다. |
| `SELLER_INTEREST_CATEGORY_REQUIRED` | 400 | SELLER_005 | 관심 카테고리는 최소 1개 이상 지정해야 합니다. |
| `SELLER_INTEREST_CATEGORY_LIMIT` | 400 | SELLER_006 | 관심 카테고리는 최대 10개까지 지정할 수 있습니다. |
| `SELLER_MUTABLE_FIELD_ONLY` | 400 | SELLER_007 | 해당 필드는 수정할 수 없습니다. |
| `SELLER_NOT_APPROVED` | 403 | SELLER_008 | 승인된 판매자만 사용할 수 있습니다. |
