# 동시성 테스트 시나리오

> 대상: **auth, category, common, demand, member, notification, product**
> 제외: groupbuy, payments, payout, order
> 목적: create/update/delete 로직에서 발생 가능한 동시성 문제(race condition, lost update, unique 위반, count 정합성)를 회귀 방지 수준에서 검증
> 도구: JUnit + `ExecutorService` / `CountDownLatch` 조합, `@SpringBootTest` + PostgreSQL TestContainer
> 규모: 스레드 2~10개, 반복 없음 (가벼운 스모크성 동시성 검증)

---

## 공통 검증 원칙

- **성공/실패 카운트 총합 = 시도 횟수** — 잃어버린 요청이 없어야 한다.
- **DB 최종 상태의 카운트/유일성이 도메인 규칙과 일치** — 예: 유니크 위반, 카운트 정확성, 상태 불변조건.
- **커밋된 트랜잭션끼리의 lost update 부재** — Advisory Lock / SELECT FOR UPDATE / partial unique index로 방어된 지점을 검증.

---

## 1. Auth 도메인

### 1-1. SocialLinkService.unlink — 마지막 로그인 수단 삭제 방지

- **테스트 대상**: `SocialLinkService.unlink`
- **전제 조건**: 한 회원이 카카오 소셜 1개 + 로컬 1개를 가진 상태 (총 2개 credential)
- **동시 시도**: `unlink(kakao)` × 스레드 A, `LocalCredentialRepository.delete` × 스레드 B (또는 두 소셜만 존재 시나리오에서 각각 unlink 동시 실행)
- **기대 결과**:
  - 정확히 1건만 성공, 나머지는 `LAST_CREDENTIAL_CANNOT_UNLINK` 예외
  - 최종 credential 총합 ≥ 1
- **검증 초점**: `AdvisoryLockAdaptor.acquireXactLock(credentialWrite(memberId))`가 count 기반 검사 phantom read를 막는지

### 1-2. SellerRegistrationService.register — 동일 사업자번호 중복

- **테스트 대상**: `SellerRegistrationService.register`
- **전제 조건**: 서로 다른 두 회원 A, B가 존재. 둘 다 아직 판매자 아님
- **동시 시도**: A, B가 같은 유효 사업자번호로 동시 register 호출
- **기대 결과**:
  - 정확히 1건만 200 OK
  - 다른 1건은 `BUSINESS_NUMBER_DUPLICATED`
  - Seller 테이블에 해당 business_number_hash 로우 1건
- **검증 초점**: `business_number_hash` unique 제약이 race 상황에서 유일성을 보장

### 1-3. SellerRegistrationService.register — 동일 회원 이중 등록

- **테스트 대상**: 같은 회원이 서로 다른 사업자번호로 동시 register
- **기대 결과**:
  - 정확히 1건만 성공
  - 다른 1건은 `SELLER_ALREADY_REGISTERED`
  - Member.isSeller=true, Seller 로우 1건
- **검증 초점**: `existsByMemberIdAndDeletedAtIsNull` 검사 이후 삽입 사이의 race

---

## 2. Member 도메인

### 2-1. ProfileService.edit — 두 회원 동일 닉네임 변경

- **테스트 대상**: `ProfileService.edit`
- **전제 조건**: 서로 다른 회원 A, B 존재. 각각 다른 닉네임 보유
- **동시 시도**: A, B가 동시에 nickname을 **"동일한 새 닉네임"**으로 변경 요청
- **기대 결과**:
  - 정확히 1건만 성공
  - 다른 1건은 `NICKNAME_DUPLICATED`
  - `member` 테이블에 해당 닉네임 로우 1건
- **검증 초점**: `existsByNicknameAndDeletedAtIsNull` 검사 이후 저장 사이의 race — 현재 lock 없음, 실패 시 개선 대상

### 2-2. ShippingAddressService.create — 5개 제한 경계

- **테스트 대상**: `ShippingAddressService.create`
- **전제 조건**: 회원이 배송지 4개 보유
- **동시 시도**: 5번째, 6번째 요청을 동시에 3~5회
- **기대 결과**:
  - 정확히 1건만 성공 (총 5개 도달)
  - 나머지는 `SHIPPING_ADDRESS_LIMIT_EXCEEDED`
  - 최종 배송지 수 정확히 5개
- **검증 초점**: `advisoryLockAdaptor.acquireXactLock(shippingAddressCreate(memberId))`가 count 기반 검사의 phantom read 방지

### 2-3. ShippingAddressService.markAsDefault — 동시에 서로 다른 배송지 기본 지정

- **테스트 대상**: `ShippingAddressService.markAsDefault`
- **전제 조건**: 배송지 A(기본), B, C 존재
- **동시 시도**: B와 C를 동시에 기본으로 지정
- **기대 결과**:
  - 두 요청 모두 성공하더라도 최종 상태는 **기본 배송지 1개**
  - 다른 두 배송지는 `is_default=false`
- **검증 초점**: `unmarkDefaultExcept` + `setAsDefault` 순서의 원자성. DB에 partial unique(`is_default=true` per member 1건) 있는지도 함께 확인.

### 2-4. ShippingAddressService.delete — 동시에 기본 배송지 삭제

- **테스트 대상**: `ShippingAddressService.delete`
- **전제 조건**: 배송지 A(기본), B, C 존재
- **동시 시도**: A를 두 번 동시에 삭제
- **기대 결과**:
  - 정확히 1건 성공 (204 No Content)
  - 다른 1건은 `SHIPPING_ADDRESS_NOT_FOUND`
  - 남은 배송지 중 가장 오래된 것이 기본으로 승격
- **검증 초점**: `deleteByIdAndMemberId`의 반환값 기반 idempotency + `promoteOldestIfNoDefault` 후처리

---

## 3. Notification 도메인

### 3-1. NotificationSettingService.edit — 동일 회원 동일 타입 동시 opt-out

- **테스트 대상**: `NotificationSettingService.edit(memberId, DEMAND_REGISTERED, false)`
- **전제 조건**: 회원 존재, opt-out 레코드 없음
- **동시 시도**: 동일 (memberId, type) 조합으로 `enabled=false` 5회 동시 호출
- **기대 결과**:
  - 최종 opt-out 레코드 정확히 **1건**만 존재
  - HTTP 응답은 모두 204 (idempotent)
- **검증 초점**: `existsByMemberIdAndType` 후 insert 사이의 race — PK (member_id, type) 복합키로 자연 방어되는지 확인

### 3-2. NotificationSettingService.edit — opt-out 저장/삭제 인터리브

- **동시 시도**: 스레드 A는 `enabled=false`(저장), 스레드 B는 `enabled=true`(삭제)를 여러 번 반복 인터리브
- **기대 결과**:
  - 최종 상태 일관성 (마지막 요청이 승자)
  - PK 위반 예외 없음
- **검증 초점**: insert-then-delete race의 무결성

---

## 4. Demand 도메인

### 4-1. DemandService.create — 동일 회원·카탈로그 중복 등록

- **테스트 대상**: `DemandService.create`
- **전제 조건**: 회원 + ACTIVE catalog + ACTIVE payMethod 준비
- **동시 시도**: 동일 catalog로 5회 동시 create 호출
- **기대 결과**:
  - 정확히 **1건만** 성공, 나머지는 `DEMAND_ALREADY_EXISTS`
  - `demand` 테이블에 (member_id, catalog_id, active status) 로우 1건
- **검증 초점**: partial unique `uq_demand_member_catalog_active`가 race 상황에서 유일성 보장, `DataIntegrityViolationException` 매핑

### 4-2. DemandService.cancel — 동시 취소 idempotency

- **테스트 대상**: `DemandService.cancel`
- **전제 조건**: ASSIGNED 상태 demand + demandBoard(participantCount=5)
- **동시 시도**: 같은 demandId를 3회 동시 cancel
- **기대 결과**:
  - 정확히 1건 성공 (204)
  - 나머지는 `DEMAND_CANCEL_NOT_ALLOWED` (이미 CANCELED)
  - `demand_board.participant_count`가 정확히 **1만큼** 감소 (5 → 4)
- **검증 초점**: `findByIdForUpdate` 락 → 상태 검사 → participantCount 감소의 원자성

### 4-3. DemandService.acceptOffer — 동시 승낙

- **테스트 대상**: `DemandService.acceptOffer`
- **전제 조건**: SUBSTITUTE_OFFERED 상태 demand + board(GB_GATHERING)
- **동시 시도**: 같은 demandId를 3회 동시 acceptOffer
- **기대 결과**:
  - 최종 상태 ASSIGNED 1건 (혹은 무효 시도는 오류)
  - `demand_board.participant_count` 정확히 1만 증가
- **검증 초점**: `increaseParticipantCountIfActive`의 조건부 UPDATE가 중복 실행 방지

### 4-4. DemandBoard 참여자 카운트 정확성 (다중 사용자)

- **테스트 대상**: 서로 다른 회원들이 동일 `DemandBoard`에 join / accept / cancel을 섞어 동시 실행
- **전제 조건**: GB_GATHERING 상태 board, 최소 5명의 회원 및 각 회원별 UNASSIGNED demand
- **동시 시도**: 회원 5명이 각각 `acceptOffer` 또는 `cancel`을 동시에 호출 (accept 3, cancel 2 등의 조합)
- **기대 결과**:
  - `demand_board.participant_count` = 성공한 accept - 성공한 관련 cancel
  - 잃어버린 증감 없음
- **검증 초점**: participant_count 갱신의 원자성 (UPDATE 기반), lost update 방지

---

## 5. Product 도메인

### 5-1. ProductCatalog 동일 name 동시 생성 (내부 API가 있다면 대상)

- **테스트 대상**: catalog 생성 로직 (테스트 시점에 노출된 엔드포인트 또는 서비스 레벨 직접 호출)
- **전제 조건**: 해당 name의 catalog가 아직 없음
- **동시 시도**: 동일 name으로 3회 동시 create
- **기대 결과**:
  - 정확히 1건만 성공
  - 나머지는 `DataIntegrityViolationException` 또는 매핑된 비즈니스 예외 (`uq_product_catalog_name` 위반)
- **검증 초점**: unique constraint가 race 하에서 유일성 보장

---

## 6. Common / 기반 인프라

### 6-1. AdvisoryLockAdaptor 자체 검증 (선택)

- **테스트 대상**: `advisoryLockAdaptor.acquireXactLock(key, "3s")`
- **동시 시도**: 동일 key로 두 트랜잭션이 동시에 획득 시도
- **기대 결과**: 후행 요청은 타임아웃 또는 대기 후 진행
- **검증 초점**: xact lock 구현이 실제로 배타적 락 역할 수행

### 6-2. EncryptionService 다중 스레드 encrypt/decrypt

- **테스트 대상**: `encrypt`/`decrypt`가 10 스레드에서 동시 호출됐을 때 결과 정합성
- **기대 결과**: encrypt-decrypt 라운드트립 성공, 손상 없음
- **검증 초점**: `TextEncryptor` 구현체의 thread-safety 회귀 검출

---

## 실행 뼈대 예시

```java
int threads = 5;
ExecutorService pool = Executors.newFixedThreadPool(threads);
CountDownLatch start = new CountDownLatch(1);
CountDownLatch done = new CountDownLatch(threads);
AtomicInteger success = new AtomicInteger();
AtomicInteger conflict = new AtomicInteger();

for (int i = 0; i < threads; i++) {
    pool.execute(() -> {
        try {
            start.await();
            service.doSomething(...);
            success.incrementAndGet();
        } catch (BusinessException e) {
            if (e.getErrorCode() == EXPECTED_CONFLICT) conflict.incrementAndGet();
        } catch (DataIntegrityViolationException e) {
            conflict.incrementAndGet();
        } finally {
            done.countDown();
        }
    });
}
start.countDown();          // 동시에 시작
done.await(10, SECONDS);
pool.shutdown();

assertThat(success.get()).isEqualTo(1);
assertThat(conflict.get()).isEqualTo(threads - 1);
// + DB 상태 검증
```

---

## 우선순위 매트릭스

| 우선순위 | 시나리오 | 이유 |
|---|---|---|
| **High** | 4-1 Demand create 중복, 4-2 Demand cancel idempotency, 2-2 배송지 5개 제한 | 실사용자 시나리오 + 유니크·카운트 정합성 핵심 |
| **High** | 1-2 사업자번호 중복, 3-1 opt-out 중복 저장 | DB 제약 회귀 감지 |
| **Medium** | 4-3, 4-4 (participantCount) | 카운트 정합성 중요하지만 인프라 부담 |
| **Medium** | 2-3, 2-4 배송지 기본/삭제 | 사용자 경험 영향 |
| **Low** | 1-1, 1-3 (auth), 5-1 catalog | 자주 발생 안 하지만 방어 필요 |
| **Low** | 6-1, 6-2 (common) | 구현 회귀 방지용 |
