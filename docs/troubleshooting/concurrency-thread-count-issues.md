# 동시성 테스트 50 스레드 변경 불가 항목 및 대체 테스트

## 요약

전체 동시성 테스트 중 아래 2개 테스트는 **비즈니스 규칙 또는 도메인 제약** 때문에
기존 시나리오를 50 스레드로 단순 확장할 수 없었습니다.
대신 각 제약 조건을 그대로 활용하는 **새로운 시나리오**를 별도 테스트로 추가했습니다.

---

## 1. `SocialLinkUnlinkConcurrencyTest`

### 기존 테스트 (1-1): 서로 다른 provider 동시 unlink
각 스레드가 서로 다른 `SocialProvider`를 unlink 시도하는 시나리오.
`SocialProvider` enum이 `KAKAO`, `GOOGLE` 2개뿐이라 스레드 수 = enum 크기에 종속되어 50 스레드 불가.

### 추가 테스트 (1-4): 동일 credential 동시 unlink
`SocialProvider.KAKAO`를 50개 스레드가 **동시에** unlink 시도.

- 선행 조건: 회원에게 KAKAO + GOOGLE credential 모두 생성 (KAKAO 삭제 후에도 GOOGLE이 남아 마지막 credential 오류 미발생)
- 기대 결과: 1건 성공, 나머지 49건은 `ErrorCode.NOT_FOUND` 실패
- 검증: 최종 credential 수 = 1 (GOOGLE만 남음)

```
기존 1-1: [Thread-0 → KAKAO unlink] [Thread-1 → GOOGLE unlink]  ← 스레드 수 = enum 크기
추가 1-4: [Thread-0..49 → 모두 KAKAO unlink]  ← 1건만 성공, 49건 NOT_FOUND
```

---

## 2. `ShippingAddressCreateConcurrencyTest`

### 기존 테스트 (2-2): 4개 있는 상태에서 동시 create
배송지가 4개인 상태에서 50개 스레드가 동시에 create → 1건 성공.
각 스레드가 **서로 다른 배송지**를 기본으로 지정하려면 50개가 필요하지만 제한은 5개이므로
"2-3: 기본 배송지 동시 지정" 테스트는 50 스레드 확장 불가.

### 추가 테스트 (2-5): 0개인 상태에서 동시 create
배송지가 **0개**인 상태에서 50개 스레드가 동시에 create.

- 선행 조건: 회원 배송지 0개
- 기대 결과: 5건 성공 (제한), 나머지 45건은 `ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED` 실패
- 검증: 최종 배송지 수 = 5

```
기존 2-2: 4개 → 50 스레드 → 1건 성공 (제한까지 1자리만 남음)
추가 2-5: 0개 → 50 스레드 → 5건 성공 (제한 5개를 동시 경쟁으로 채움)
```

---

## 변경 완료된 테스트 목록

| 테스트 | 변경 전 | 변경 후 |
|--------|---------|---------|
| `EncryptionServiceConcurrencyTest` | 10 | 50 |
| `ShippingAddressCreateConcurrencyTest` 2-2 | 5 | 50 |
| `ShippingAddressCreateConcurrencyTest` 2-5 | — | 신규 추가 (0개 → 5건 성공) |
| `ShippingAddressDefaultDeleteConcurrencyTest` 2-4 | 2 | 50 |
| `DemandCreateConcurrencyTest` | 5 | 50 |
| `DemandCancelConcurrencyTest` | 3 | 50 |
| `DemandParticipantCountConcurrencyTest` 4-3 | 3 | 50 |
| `DemandParticipantCountConcurrencyTest` 4-4 | 5 (3+2) | 50 (30+20) |
| `NotificationOptOutConcurrencyTest` | 5 | 50 |
| `NotificationOptOutInterleaveConcurrencyTest` | 6 | 50 |
| `ProductCatalogCreateConcurrencyTest` | 3 | 50 |
| `SellerRegistrationBusinessNumberConcurrencyTest` | 50 | 50 (기존 유지) |
| `SellerRegistrationSameMemberConcurrencyTest` | 50 | 50 (기존 유지) |
| `SocialLinkUnlinkConcurrencyTest` 1-1 | 2 | 2 (enum 크기 종속, 유지) |
| `SocialLinkUnlinkConcurrencyTest` 1-4 | — | 신규 추가 (동일 provider 동시 unlink) |
| `ShippingAddressDefaultDeleteConcurrencyTest` 2-3 | 2 | 2 (배송지 5개 제한, 유지) |
