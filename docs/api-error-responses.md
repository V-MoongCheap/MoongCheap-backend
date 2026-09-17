# API 실패 응답 규격

## 공통 응답 형식

```json
{
  "code": "에러코드",
  "message": "에러 메시지",
  "fieldErrors": [
    { "field": "필드명", "message": "필드 에러 메시지" }
  ]
}
```

> `fieldErrors`는 400 유효성 검사 실패 시에만 포함됩니다. 나머지는 빈 배열 `[]`입니다.

---

## 공통 에러 (전 엔드포인트)

| HTTP | code | message |
|------|------|---------|
| 400 | COMMON_400 | 입력값이 올바르지 않습니다. |
| 401 | COMMON_401 | 인증이 필요합니다. |
| 403 | COMMON_403 | 접근 권한이 없습니다. |
| 404 | COMMON_404 | 리소스를 찾을 수 없습니다. |
| 409 | COMMON_409 | 요청이 충돌했습니다. 잠시 후 다시 시도해주세요. |
| 500 | COMMON_500 | 서버 오류가 발생했습니다. |

---

## 엔드포인트별 비즈니스 에러

### Auth

#### `POST /api/auth/signup` — 아이디 회원가입
| HTTP | code | message |
|------|------|---------|
| 400 | AUTH_003 | 비밀번호 형식이 올바르지 않습니다. |
| 400 | AUTH_004 | 비밀번호가 일치하지 않습니다. |
| 400 | AUTH_005 | 비밀번호에 아이디를 포함할 수 없습니다. |
| 409 | AUTH_001 | 이미 사용 중인 아이디입니다. |
| 409 | AUTH_015 | 회원가입에 실패했습니다. 잠시 후 다시 시도해주세요. (동시성 충돌) |

#### `POST /api/auth/login` — 아이디 로그인
| HTTP | code | message |
|------|------|---------|
| 401 | AUTH_006 | 아이디 또는 비밀번호가 올바르지 않습니다. |
| 423 | AUTH_007 | 연속 로그인 실패로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요. |

#### `PATCH /api/auth/password` — 비밀번호 변경
| HTTP | code | message |
|------|------|---------|
| 400 | AUTH_004 | 비밀번호가 일치하지 않습니다. |
| 400 | AUTH_008 | 비밀번호가 등록된 회원만 사용할 수 있습니다. |
| 400 | AUTH_009 | 이전과 동일한 비밀번호는 사용할 수 없습니다. |
| 401 | AUTH_006 | 아이디 또는 비밀번호가 올바르지 않습니다. |

#### `DELETE /api/auth/withdraw` — 회원 탈퇴
| HTTP | code | message |
|------|------|---------|
| 400 | AUTH_003 | 비밀번호 형식이 올바르지 않습니다. |
| 401 | AUTH_006 | 아이디 또는 비밀번호가 올바르지 않습니다. |
| 404 | USER_001 | 회원을 찾을 수 없습니다. |

#### `POST /api/sellers` — 판매자 등록
| HTTP | code | message |
|------|------|---------|
| 400 | SELLER_002 | 사업자등록번호 형식이 올바르지 않습니다. |
| 404 | USER_001 | 회원을 찾을 수 없습니다. |
| 409 | SELLER_001 | 이미 판매자로 등록되어 있습니다. |
| 409 | SELLER_003 | 이미 등록된 사업자등록번호입니다. |

#### `POST /api/auth/logout` — 로그아웃
비즈니스 에러 없음.

#### `GET /api/auth/social-links/{provider}` — 소셜 계정 연동 시작
비즈니스 에러 없음. (OAuth2 인가 페이지로 redirect)

#### `DELETE /api/auth/social-links/{provider}` — 소셜 계정 연동 해제
| HTTP | code | message |
|------|------|---------|
| 400 | AUTH_012 | 마지막 로그인 수단은 해제할 수 없습니다. |
| 404 | COMMON_404 | 리소스를 찾을 수 없습니다. |

#### `POST /api/auth/social-signup/complete` — 소셜 가입 완료
| HTTP | code | message |
|------|------|---------|
| 400 | AUTH_013 | 이미 소셜 가입이 완료되었습니다. |
| 404 | USER_001 | 회원을 찾을 수 없습니다. |

---

### Member

#### `GET /api/members/me` — 내 프로필 조회
| HTTP | code | message |
|------|------|---------|
| 404 | USER_001 | 회원을 찾을 수 없습니다. |

#### `PATCH /api/members/me` — 내 프로필 수정
| HTTP | code | message |
|------|------|---------|
| 400 | USER_003 | 닉네임 형식이 올바르지 않습니다. |
| 404 | USER_001 | 회원을 찾을 수 없습니다. |
| 409 | USER_002 | 이미 사용 중인 닉네임입니다. |

#### `GET /api/members/nicknames/availability` — 닉네임 중복 검사
비즈니스 에러 없음.

#### `GET /api/sellers/{id}/public` — 판매자 공개 정보 조회
| HTTP | code | message |
|------|------|---------|
| 404 | SELLER_004 | 판매자 정보를 찾을 수 없습니다. |

---

### Shipping Address

#### `GET /api/shipping-addresses` — 배송지 목록
비즈니스 에러 없음.

#### `GET /api/shipping-addresses/{id}` — 배송지 상세
| HTTP | code | message |
|------|------|---------|
| 403 | SHIP_003 | 본인 소유의 배송지가 아닙니다. |
| 404 | SHIP_001 | 배송지를 찾을 수 없습니다. |

#### `POST /api/shipping-addresses` — 배송지 등록
| HTTP | code | message |
|------|------|---------|
| 400 | SHIP_002 | 배송지는 최대 5개까지 등록할 수 있습니다. |

#### `PATCH /api/shipping-addresses/{id}` — 배송지 수정
| HTTP | code | message |
|------|------|---------|
| 403 | SHIP_003 | 본인 소유의 배송지가 아닙니다. |
| 404 | SHIP_001 | 배송지를 찾을 수 없습니다. |

#### `DELETE /api/shipping-addresses/{id}` — 배송지 삭제
| HTTP | code | message |
|------|------|---------|
| 403 | SHIP_003 | 본인 소유의 배송지가 아닙니다. |
| 404 | SHIP_001 | 배송지를 찾을 수 없습니다. |

#### `PATCH /api/shipping-addresses/{id}/default` — 기본 배송지 지정
| HTTP | code | message |
|------|------|---------|
| 404 | SHIP_001 | 배송지를 찾을 수 없습니다. |
| 409 | SHIP_004 | 기본 배송지 변경이 충돌했습니다. 다시 시도해주세요. |

---

### Notification

#### `GET /api/members/me/notification-settings` — 알림 설정 목록
비즈니스 에러 없음.

#### `PATCH /api/members/me/notification-settings/{type}` — 알림 설정 변경
| HTTP | code | message |
|------|------|---------|
| 400 | COMMON_400 | 필수 알림은 해제할 수 없습니다. |

---

### Demand

#### `GET /api/members/me/demand` — 수요 목록 조회
비즈니스 에러 없음.

#### `POST /api/members/me/demand` — 수요 등록
| HTTP | code | message |
|------|------|---------|
| 404 | PRODUCT_001 | 상품 카탈로그를 찾을 수 없습니다. |
| 404 | PAY_001 | 유효한 결제 수단을 찾을 수 없습니다. |
| 409 | DEMAND_001 | 이미 진행 중인 수요 요청이 있습니다. |

#### `GET /api/members/me/demand/{demandId}` — 수요 단건 조회
| HTTP | code | message |
|------|------|---------|
| 404 | DEMAND_002 | 수요 요청을 찾을 수 없습니다. |

#### `DELETE /api/members/me/demand/{demandId}` — 수요 취소
| HTTP | code | message |
|------|------|---------|
| 400 | DEMAND_005 | 현재 상태에서는 수요를 취소할 수 없습니다. |
| 403 | DEMAND_003 | 본인의 수요 요청이 아닙니다. |
| 404 | DEMAND_002 | 수요 요청을 찾을 수 없습니다. |

#### `PATCH /api/members/me/demand/{demandId}/accept` — 대체 오퍼 승낙
| HTTP | code | message |
|------|------|---------|
| 400 | DEMAND_007 | 현재 상태에서는 대체 오퍼를 승낙할 수 없습니다. (demandBoardId 미배정) |
| 400 | DEMAND_009 | 수요 희망 기간이 만료되었습니다. |
| 404 | DEMAND_002 | 수요 요청을 찾을 수 없습니다. |

#### `PATCH /api/members/me/demand/{demandId}/reject` — 대체 오퍼 거절
| HTTP | code | message |
|------|------|---------|
| 400 | DEMAND_007 | 현재 상태에서는 대체 오퍼를 거절할 수 없습니다. (demandBoardId 미배정) |
| 400 | DEMAND_009 | 수요 희망 기간이 만료되었습니다. |
| 404 | DEMAND_002 | 수요 요청을 찾을 수 없습니다. |

---

### DemandBoard

#### `GET /api/demand-boards` — 상위 수요 보드 수집
비즈니스 에러 없음.

#### `GET /api/demand-boards/exists` — 수요 보드 존재 여부 확인
비즈니스 에러 없음.

#### `GET /api/demand-boards/{demandBoardId}` — 수요 보드 단건 조회
| HTTP | code | message |
|------|------|---------|
| 404 | DEMAND_004 | 수요 보드를 찾을 수 없습니다. |

#### `POST /api/demand-boards/{demandBoardId}/join` — 수요 보드 참가
| HTTP | code | message |
|------|------|---------|
| 400 | DEMAND_006 | 마감된 수요 보드입니다. |
| 404 | DEMAND_004 | 수요 보드를 찾을 수 없습니다. (GB_GATHERING 상태 아님 포함) |
| 404 | PAY_001 | 유효한 결제 수단을 찾을 수 없습니다. |
| 409 | DEMAND_001 | 이미 진행 중인 수요 요청이 있습니다. |

#### `GET /api/demand-boards/{demandBoardId}/auction-result` — 낙찰 결과 조회
| HTTP | code | message |
|------|------|---------|
| 404 | DEMAND_004 | 수요 보드를 찾을 수 없습니다. (GB_ACTION_REQUIRED 상태 아님 또는 미참여 포함) |

#### `GET /api/demand-boards/catalog/{catalogId}` — 상품 도감 기준 수요 보드 수집
비즈니스 에러 없음.

#### `POST /api/demand-boards/internal/formation-plans` — 편성 계획 적용 (내부 API)
항목별 오류는 응답 body의 `results`에 포함되어 반환되며 HTTP 에러로 전파되지 않음.

#### `POST /api/demand-boards/internal/substitute-offer-plans` — 대체 오퍼 제안 계획 적용 (내부 API)
항목별 오류는 응답 body의 `results`에 포함되어 반환되며 HTTP 에러로 전파되지 않음.

---

### Awarding (내부 API)

#### `GET /api/awarding/pending` — AI 판정 대기 보드 조회
비즈니스 에러 없음.

#### `POST /api/awarding/internal/result` — AI 낙찰 결과 반영
| HTTP | code | message |
|------|------|---------|
| 404 | DEMAND_004 | 수요 보드를 찾을 수 없습니다. |
| 409 | DEMAND_013 | 낙찰 결과와 상품 상태가 일치하지 않습니다. |
| 409 | DEMAND_014 | 낙찰 대상 참여 수요가 존재하지 않습니다. |

---

### ProductCatalog

#### `GET /api/product-catalog` — 상위 상품 도감 조회
비즈니스 에러 없음.

#### `GET /api/product-catalog/{id}` — 상품 도감 상세 조회
| HTTP | code | message |
|------|------|---------|
| 404 | PRODUCT_001 | 상품 카탈로그를 찾을 수 없습니다. |

---

### Order

#### `GET /api/orders/list` — 주문 목록 조회
| HTTP | code | message |
|------|------|---------|
| 404 | USER_001 | 회원을 찾을 수 없습니다. |

#### `GET /api/orders/{orderNo}` — 주문 상세 조회
| HTTP | code | message |
|------|------|---------|
| 404 | USER_001 | 회원을 찾을 수 없습니다. |
| 404 | ORDER_001 | 주문을 찾을 수 없습니다. |

#### `PATCH /api/orders/{orderNo}/cancel` — 주문 취소
| HTTP | code | message |
|------|------|---------|
| 404 | USER_001 | 회원을 찾을 수 없습니다. |
| 404 | ORDER_001 | 주문을 찾을 수 없습니다. |
| 409 | ORDER_002 | 현재 상태에서는 주문을 취소할 수 없습니다. |

#### `POST /api/orders/{orderNo}/shipping-address` — 배송지 입력
| HTTP | code | message |
|------|------|---------|
| 404 | USER_001 | 회원을 찾을 수 없습니다. |
| 404 | ORDER_001 | 주문을 찾을 수 없습니다. |
| 409 | ORDER_003 | 현재 상태에서는 배송지를 입력할 수 없습니다. |

---

### GroupBuy

#### `GET /api/group-buys` — 공동구매 목록 조회
비즈니스 에러 없음.

#### `GET /api/group-buys/{groupBuyId}` — 공동구매 상세 조회
| HTTP | code | message |
|------|------|---------|
| 404 | GROUPBUY_001 | 공동구매를 찾을 수 없습니다. |

---

### ProductSearch (내부 API)

#### `POST /api/products-search/internal` — 상품 단건 색인
| HTTP | code | message |
|------|------|---------|
| 404 | - | 상품 카탈로그를 찾을 수 없습니다. (ResponseStatusException) |

#### `POST /api/products-search/internal/bulk` — 상품 일괄 색인
| HTTP | code | message |
|------|------|---------|
| 404 | PRODUCT_001 | 상품 카탈로그를 찾을 수 없습니다. |

#### `DELETE /api/products-search/internal/{id}` — 상품 색인 삭제
비즈니스 에러 없음.

#### `GET /api/products-search/search` — 상품 검색
비즈니스 에러 없음.

---

### Payment

#### `POST /api/payments/methods` — 결제수단 등록

현재 별도의 비즈니스 에러가 없습니다.

#### `GET /api/payments/methods` — 결제수단 목록 조회

현재 별도의 비즈니스 에러가 없습니다.

#### `DELETE /api/payments/methods/{paymentMethodId}` — 결제수단 삭제

현재 별도의 비즈니스 에러가 없습니다.

#### `PATCH /api/payments/{paymentId}` — 결제 취소

현재 별도의 비즈니스 에러가 없습니다.
