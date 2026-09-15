# Enum 값 정리

> Status 값(생애주기 계열)은 [status.md](status.md)를 참조하세요.
> 이 문서는 상태 전이가 없는 비-Status 계열 enum(역할, 제공자, 알림 종류, 에러 코드 등)을 다룹니다.

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
| `SOCIAL_SIGNUP_ALREADY_COMPLETE` | 400 | AUTH_013 | 이미 소셜 가입이 완료되었습니다. |
| `WITHDRAW_BLOCKED_HAS_ORDER` | 400 | AUTH_014 | 진행 중인 거래가 있어 탈퇴할 수 없습니다. |
| `CONCURRENT_SIGNUP_CONFLICT` | 409 | AUTH_015 | 회원가입에 실패했습니다. 잠시 후 다시 시도해주세요. |
| `SOCIAL_SIGNUP_INCOMPLETE` | 403 | AUTH_016 | 소셜 가입 완료가 필요합니다. |

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
| `SELLER_MUTABLE_FIELD_ONLY` | 400 | SELLER_007 | 해당 필드는 수정할 수 없습니다. |
| `SELLER_NOT_APPROVED` | 403 | SELLER_008 | 승인된 판매자만 사용할 수 있습니다. |

### Product

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `PRODUCT_CATALOG_NOT_FOUND` | 404 | PRODUCT_001 | 상품 카탈로그를 찾을 수 없습니다. |
| `PRODUCT_NOT_ORDERABLE` | 409 | PRODUCT_002 | 현재 주문할 수 없는 상품입니다. |

### Demand

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `DEMAND_ALREADY_EXISTS` | 409 | DEMAND_001 | 이미 진행 중인 수요 요청이 있습니다. |
| `DEMAND_NOT_FOUND` | 404 | DEMAND_002 | 수요 요청을 찾을 수 없습니다. |
| `DEMAND_FORBIDDEN` | 403 | DEMAND_003 | 본인의 수요 요청이 아닙니다. |
| `DEMAND_BOARD_NOT_FOUND` | 404 | DEMAND_004 | 수요 보드를 찾을 수 없습니다. |
| `DEMAND_CANCEL_NOT_ALLOWED` | 400 | DEMAND_005 | 현재 상태에서는 수요를 취소할 수 없습니다. |
| `DEMAND_BOARD_CLOSED` | 400 | DEMAND_006 | 마감된 수요 보드입니다. |
| `DEMAND_ACCEPT_NOT_ALLOWED` | 400 | DEMAND_007 | 현재 상태에서는 대체 오퍼를 승낙할 수 없습니다. |
| `DEMAND_ACCEPT_CATALOG_CONFLICT` | 409 | DEMAND_008 | 제안된 상품과 동일한 상품의 진행 중인 수요가 이미 존재하여 승낙할 수 없습니다. |
| `DEMAND_DESIRE_EXPIRED` | 400 | DEMAND_009 | 수요 희망 기간이 만료되었습니다. |
| `DEMAND_ASSIGNMENT_MISMATCH` | 400 | DEMAND_010 | 일부 수요가 이미 배정되었거나 유효하지 않은 상태입니다. |
| `DEMAND_SUBSTITUTE_NOT_ELIGIBLE` | 409 | DEMAND_011 | 대체 오퍼를 받을 수 없는 수요입니다. |
| `DEMAND_SUBSTITUTE_ALREADY_APPLIED` | 409 | DEMAND_012 | 이미 저장된 대체 제안입니다. |
| `DEMAND_BOARD_AWARDING_INCONSISTENT` | 409 | DEMAND_013 | 낙찰 결과와 상품 상태가 일치하지 않습니다. |
| `DEMAND_BOARD_NO_PARTICIPANT` | 409 | DEMAND_014 | 낙찰 대상 참여 수요가 존재하지 않습니다. |

### Order

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `ORDER_NOT_FOUND` | 404 | ORDER_001 | 주문을 찾을 수 없습니다. |
| `ORDER_CANNOT_CANCEL` | 409 | ORDER_002 | 현재 상태에서는 주문을 취소할 수 없습니다. |
| `ORDER_CANNOT_SHIPPING` | 409 | ORDER_003 | 현재 상태에서는 배송지를 입력할 수 없습니다. |

### GroupBuy

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `GROUPBUY_NOT_FOUND` | 404 | GROUPBUY_001 | 공동구매를 찾을 수 없습니다. |
| `GROUPBUY_NOT_OPEN` | 409 | GROUPBUY_002 | 현재 주문할 수 없는 공동구매입니다. |

### Search

| 값 | HTTP 상태 | 코드 | 메시지 |
|---|---|---|---|
| `SEARCH_INDEX_FAILED` | 500 | SEARCH_001 | 상품 검색 인덱싱에 실패했습니다. |
