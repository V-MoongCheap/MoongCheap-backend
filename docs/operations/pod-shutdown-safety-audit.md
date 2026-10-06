# Pod 강제 종료(SIGKILL) 시 정합성 문제 및 개선안=

## 배경

Kubernetes 환경에서 Pod가 종료되는 방식은 다양하며, **`@PreDestroy`가 실행되지 않는 시나리오**가 다수 존재합니다:

| 종료 방식                                 | SIGTERM | `@PreDestroy` |
|---------------------------------------|---------|---------------|
| 롤링 업데이트 (grace period 초과)             | ✅       | 시간 초과 시 미실행   |
| `kubectl delete pod --grace-period=0` | ❌       | ❌             |
| Node 장애 (하드웨어)                        | ❌       | ❌             |
| OOMKilled (커널)                        | ❌       | ❌             |
| Spot 인스턴스 즉시 회수                       | 짧게만     | 대부분 미실행       |

결제/주문 파트는 이미 Redis lease + DB `UNKNOWN` 상태 + 토스 idempotencyKey로 4중 방어되어 안전합니다. 그러나 **인증(auth) 계열
코드에서 트랜잭션 커밋 순서와 외부 부수효과 순서의 문제**가 발견되어 정리합니다.

---

## 🔴 Critical: `WithdrawService` — 외부 언링크와 DB 트랜잭션 순서 문제

### 파일 위치

`src/main/java/com/moongcheap_backend/auth/application/WithdrawService.java:45-73`

### 문제 코드

```java

@Transactional
public void withdraw(Long memberId, WithdrawRequestDto request) {
    Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)...;
    Optional<LocalCredential> localCredential = ...;

    // (검증 로직)
    if (localCredential.isPresent())
        verifyLocalWithdraw(request, localCredential.get());
    else
        verifySocialWithdraw();
    eligibilityChecker.ensureWithdrawable(memberId);

    List<SocialCredential> socials = socialCredentialRepository.findAllByMemberId(memberId);
    String googleAccessToken = currentGoogleAccessToken();

    sessionManager.invalidateAllForMember(memberId);              // (a) Redis 세션 무효화
    shippingAddressRepository.deleteAllByMemberId(memberId);      // (b) DB 삭제
    notificationOptOutRepository.deleteAllByMemberId(memberId);   // (c) DB 삭제
    socialCredentialRepository.deleteByMemberId(memberId);        // (d) DB 삭제
    localCredentialRepository.deleteByMemberId(memberId);         // (e) DB 삭제
    member.withdraw();                                            // (f) DB 상태 변경

    unlinkFromProviders(socials,
        googleAccessToken);              // (g) ★ 외부 API 호출 (Kakao/Google unlink)
}   // ← 여기서 트랜잭션 커밋
```

### 왜 문제인가

**핵심 문제**: 외부 API 호출인 `unlinkFromProviders(g)`가 `@Transactional` 메서드 내부에서 **DB 커밋 직전**에 실행됩니다. 외부
API는 DB 트랜잭션의 통제를 받지 않기 때문에, 아래 순서로 부분 실패가 발생할 수 있습니다.

#### 문제 시나리오 단계별 분석

1. (a)~(f)까지 정상 실행됨. DB 변경은 아직 트랜잭션 내부에 pending 상태.
2. (g)에서 **Kakao/Google에 실제 언링크 HTTP 요청 전송** → 외부 provider는 즉시 해당 소셜 계정 연결을 끊음.
3. **바로 이 시점에 SIGKILL 발생** (배포, 노드 장애, OOMKilled 등).
4. `@Transactional`의 커밋 훅이 실행되지 못하고 프로세스가 죽음.
5. DB 연결이 유실되면서 PostgreSQL이 트랜잭션을 **자동 롤백**.
6. DB의 모든 변경 사항 원상복구.

#### 결과 상태 (불일치 발생)

| 저장소                    | 최종 상태     | 설명                    |
|------------------------|-----------|-----------------------|
| Kakao/Google (외부)      | 언링크 완료    | 되돌릴 방법 없음             |
| Redis 세션               | 무효화됨      | (a)는 트랜잭션 외부라 롤백되지 않음 |
| DB `member.deletedAt`  | `null`    | 롤백됨, 여전히 활성 회원        |
| DB `social_credential` | 존재        | 롤백됨, 아직 연결된 것처럼 남음    |
| DB `local_credential`  | 존재 (있었다면) | 롤백됨                   |

**최종 문제**:

- 사용자는 시스템상 여전히 활성 회원.
- 그러나 Kakao/Google 로그인 시도 시 provider가 "연결되지 않음"으로 응답.
- 로컬 비밀번호가 있으면 그걸로 로그인 가능하지만, **소셜 전용 가입자는 로그인 자체가 불가능**.
- 사용자 스스로 복구 불가. **관리자 개입 없이는 영구적으로 계정 접근 차단**.

### 위험도

**🔴 Critical**

- **발생 확률**: 낮음 (탈퇴 요청과 SIGKILL이 정확히 겹쳐야 함)
- **영향도**: 매우 큼 (계정 접근 영구 불가, 수동 복구 필요)
- **감지 난이도**: 매우 어려움 (트랜잭션 로그가 아예 없어 사후 추적도 힘듦)
- **재발 방지**: 코드 구조를 바꾸지 않는 한 상시 리스크

### 개선 방향

#### 방안 A: Outbox 패턴으로 언링크 이벤트 분리 (권장)

```java

@Transactional
public void withdraw(Long memberId, WithdrawRequestDto request) {
    // 검증 및 DB 상태 변경만 수행
    ...
    member.withdraw();

    // 언링크 요청을 outbox 이벤트로 저장 (같은 트랜잭션 내)
    for (SocialCredential cred : socials) {
        outboxRepository.save(OutboxEvent.socialUnlink(
            memberId, cred.getProvider(), cred.getProviderId(), googleAccessToken
        ));
    }
    // ← 트랜잭션 커밋 시점에 DB 변경 + outbox 이벤트가 원자적으로 커밋됨
}

// 별도 스케줄러가 outbox를 폴링해서 실제 unlink 실행 후 outbox 상태를 published로 변경
```

**장점**:

- DB 커밋 완료된 회원만 언링크 대상이 됨. 롤백된 케이스는 outbox에도 남지 않음.
- 언링크 API 실패 시 재시도 가능 (다음 스케줄 사이클).
- 프로젝트에 이미 `OutboxEvent` 패턴이 있어 자연스럽게 확장 가능.

#### 방안 B: 언링크를 먼저 시도하고 성공한 것만 DB 반영

```java

@Transactional
public void withdraw(Long memberId, WithdrawRequestDto request) {
    // 검증
    ...
    // 먼저 외부 언링크 시도
    List<SocialCredential> unlinkSucceeded = new ArrayList<>();
    for (SocialCredential cred : socials) {
        try {
            unlinkProvider(cred, googleAccessToken);
            unlinkSucceeded.add(cred);
        } catch (Exception e) {
            log.warn("Provider unlink failed: memberId={}, provider={}", memberId,
                cred.getProvider(), e);
        }
    }
    // 성공한 것만 DB에서 삭제
    ...
}
```

**단점**:

- 외부 API 지연이 트랜잭션 시간에 포함됨 → DB 락 유지 시간 길어짐.
- 외부 API 실패 시 부분 언링크 상태로 남을 수 있음.

**권장**: **방안 A (Outbox)**. 프로젝트 전반의 패턴과도 일관됨.

---

## 🔴 추가 개선 사항: OAuth 로그인 실패 시 실패 사유 WARN 로그 추가

### 배경

현재 OAuth2 로그인 흐름에서 실패 시나리오에 대한 상세 로그가 부족합니다. 문제 발생 시 원인 추적이 어렵고, 특히 프로덕션에서 다음과 같은 상황을 구분하기 힘듭니다:

- 카카오/구글 API의 일시 장애인지, 우리 애플리케이션 문제인지
- 사용자가 이미 탈퇴한 계정으로 로그인 시도한 경우인지
- 세션 저장 실패인지, 토큰 발급 실패인지
- 필수 사용자 정보가 provider에서 안 온 경우인지

로그가 없으면 **CS 인입 시 원인 파악에만 오랜 시간이 소요**되고, 실제 인시던트 발생 시에도 근본 원인을 찾기 어렵습니다.

### 필요 조치

`OAuth2LoginSuccessHandler`, `CustomOAuth2UserService`, 그리고 실패 핸들러(있다면 `OAuth2LoginFailureHandler`)에
다음 케이스별 WARN 로그를 추가해야 합니다.

#### 로그 필요 케이스

1. **`SessionPrincipal` 속성 누락** — `CustomOAuth2UserService`에서 principal 저장 실패한 경우
   ```java
   log.warn("OAuth2 login failed: SessionPrincipal missing after authentication, provider={}, providerId={}",
       provider, providerId);
   ```

2. **소셜 계정 조회 실패** — provider 응답에서 필수 필드가 없는 경우
   ```java
   log.warn("OAuth2 login failed: required user info missing, provider={}, missingFields={}",
       provider, missingFields);
   ```

3. **회원 상태 이상** — 탈퇴 회원이 재로그인 시도하는 경우 등
   ```java
   log.warn("OAuth2 login failed: member in unusable state, memberId={}, provider={}, reason={}",
       memberId, provider, reason);
   ```

4. **세션 바인딩 실패** — `sessionManager.bindPrincipal` 예외
   ```java
   log.warn("OAuth2 login failed: session binding failed, memberId={}, provider={}",
       memberId, provider, exception);
   ```

5. **Google access token 추출 실패** — 토큰 저장 로직에서 client가 null인 경우
   ```java
   log.warn("OAuth2 login: Google access token unavailable, memberId={}", memberId);
   ```

6. **provider별 예외** — Kakao/Google API 호출 자체가 실패한 경우
   ```java
   log.warn("OAuth2 login failed: provider API call failed, provider={}, endpoint={}",
       provider, endpoint, exception);
   ```

#### 로그에 포함해야 할 공통 필드

- `provider` (kakao/google)
- `providerId` (해싱하거나 마스킹 처리)
- `memberId` (있는 경우)
- `reason` 또는 `errorCode`
- `traceId` (있다면 MDC에 세팅되어 있어야 함)

#### 로그 레벨 기준

- **WARN**: 사용자 조작 실수, 외부 provider 일시 장애, 회원 상태 이상 등 (재현 가능한 케이스)
- **ERROR**: 시스템 버그, 예상하지 못한 예외 (즉시 조치 필요)
- **개인정보(이메일, 실명 등)는 절대 로그에 남기지 않음.**

---

## 🟡 Medium: `PaymentRecoveryScheduler` — 인메모리 커서 손실

### 파일 위치

`src/main/java/com/moongcheap_backend/payments/application/PaymentRecoveryScheduler.java:24-25`

### 문제 코드

```java
private final AtomicLong orderCursor = new AtomicLong();
private final AtomicLong outboxCursor = new AtomicLong();

@Scheduled(fixedDelayString = "${moongcheap.payments.queue.recovery-delay-ms:5000}")
public void recover() {
    var orderIds = ordersRepository.findUnscheduledPaymentOrderIds(orderCursor.get(), ...);
    if (orderIds.isEmpty())
        orderCursor.set(0);
    for (Long orderId : orderIds) {
        orderCursor.set(orderId);
        try {
            preparationService.schedule(orderId);
        } catch (...){ ...}
    }
    ...
}
```

### 왜 문제인가

- `AtomicLong`은 JVM 힙 메모리에만 존재하는 **인스턴스 로컬 상태**.
- Pod가 종료되면 값이 소실되고, 재기동 시 커서가 `0`으로 리셋됨.
- 여러 Pod가 있으면 각 Pod의 커서가 **독립적**이라 서로 알지 못함.

#### 발생하는 비효율

1. 재기동마다 **`orderCursor=0`부터 다시 스캔** → 이미 처리 완료된 낮은 ID 범위를 계속 재검토.
2. `findUnscheduledPaymentOrderIds`가 매 사이클마다 orders 테이블 처음부터 스캔 → 불필요한 DB I/O.
3. 여러 Pod 환경에서는 Pod 수만큼 중복 스캔 발생.
4. Order 수가 늘어날수록 배치 사이클이 느려지며, 최악의 경우 recovery-delay-ms 안에 배치를 못 끝냄.

#### 정합성 관점

- **정합성 자체는 안전함**: `preparationService.schedule()`이 내부적으로
  `findFirstByOrdersIdOrderByIdDesc(orderId).isPresent()` 체크로 이미 처리된 항목을 스킵.
- 즉 **중복 처리는 발생하지 않지만, 중복 조회/판별로 인한 부하만 늘어남**.

### 위험도

**🟡 Medium**

- 정합성: ✅ 안전
- 성능: 재기동 빈도 × Pod 수 × orders 테이블 크기에 비례해서 악화
- 실제 인시던트 유발 가능성: 대규모 오더 누적 상태에서 재기동 시 recovery가 지연되어 결제 스케줄 지연 발생 가능

### 개선 방향

#### 방안 A: DB 커서 테이블 (권장)

```sql
CREATE TABLE scheduler_cursor
(
    scheduler_name    VARCHAR(64) PRIMARY KEY,
    last_processed_id BIGINT    NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);
```

```java
public void recover() {
    long cursor = cursorRepository.findByName("payment-order-recovery")
        .map(SchedulerCursor::getLastProcessedId).orElse(0L);
    var orderIds = ordersRepository.findUnscheduledPaymentOrderIds(cursor, ...);
    for (Long orderId : orderIds) {
        preparationService.schedule(orderId);
        cursorRepository.updateCursor("payment-order-recovery", orderId);  // 별도 트랜잭션
    }
}
```

**장점**:

- 재기동 후에도 커서 지속.
- 여러 Pod가 하나의 커서를 공유 (락 필요 또는 SKIP LOCKED 패턴 적용).
- 스케줄러 진행 상황을 DB에서 관찰 가능.

#### 방안 B: Redis에 커서 저장

간단하지만 Redis 유실 시 커서도 유실됨. Payment 도메인은 이미 Redis 유실 복구 로직이 있으니 무해하지만 관찰성은 떨어짐.

**권장**: **방안 A**. 스케줄러 상태를 DB에 남기는 것이 가장 안전하고 관찰 가능성도 높음.

---

## 🟡 Medium: `PasswordChangeService` — 세션 무효화 순서 문제

### 파일 위치

`src/main/java/com/moongcheap_backend/auth/application/PasswordChangeService.java:27-45`

### 문제 코드

```java

@Transactional
public void changePassword(Long memberId, ChangePasswordRequestDto request,
    HttpServletRequest httpRequest) {
    ...
    credential.changePassword(
        passwordEncoder.encode(request.newPassword()));  // (a) DB 변경 (pending)
    sessionManager.invalidateAllExceptCurrent(memberId, httpRequest);           // (b) Redis 세션 무효화
}   // ← 여기서 DB 커밋
```

### 왜 문제인가

- (a) 비밀번호 변경은 아직 트랜잭션 내부에 있어 커밋되지 않은 상태.
- (b) 세션 무효화는 Redis 직접 조작이라 **트랜잭션 롤백과 무관하게 즉시 반영**됨.
- 즉 두 작업이 원자적으로 묶여있지 않고, 부수효과가 먼저 나가는 형태.

#### 문제 시나리오 단계별 분석

1. (a) 실행 → JPA 영속성 컨텍스트에 새 비밀번호 write 등록 (아직 DB 반영 X).
2. (b) 실행 → Redis에서 다른 디바이스 세션들이 **즉시 삭제됨**.
3. 트랜잭션 커밋 직전 SIGKILL.
4. DB 연결 유실 → PostgreSQL이 pending 트랜잭션 롤백 → 비밀번호는 **원래 값 그대로 유지**.

#### 결과 상태 (불일치 발생)

- **DB**: 이전 비밀번호 그대로.
- **Redis**: 다른 디바이스 세션은 이미 삭제됨.
- **사용자 체감**:
    - 다른 디바이스에서 갑자기 로그아웃됨.
    - 비밀번호를 바꿨다고 생각했는데 **이전(원래) 비밀번호로 재로그인 가능**.
    - "비번을 바꿨는데 왜 원래 걸로 되지?" 하는 혼란.

### 위험도

**🟡 Medium (UX 관점)**

- 정합성: 완전한 데이터 문제는 아니지만 사용자 경험 관점에서 일관성 없음.
- 보안: 오히려 방어적 (세션이 무효화된 상태라 실제 침해는 없음).
- CS 인입 증가 요인: "비번을 바꿨는데 이전 비번으로 로그인됨" 문의 발생.

### 개선 방향

#### 방안 A: `TransactionSynchronization.afterCommit()` 활용 (권장)

```java

@Transactional
public void changePassword(...) {
    ...
    credential.changePassword(...);

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            sessionManager.invalidateAllExceptCurrent(memberId, httpRequest);
        }
    });
}
```

**동작 방식**:

- 트랜잭션 커밋이 완전히 성공한 후에만 세션 무효화가 실행됨.
- SIGKILL이 커밋 직전에 발생하면 무효화도 실행되지 않음 → **DB와 세션 상태가 항상 일관성 유지**.

**주의**: 이 방식도 커밋 후 무효화 실행 직전에 SIGKILL이 오면 무효화가 스킵됨. 다만 그 경우 "비번은 바뀌었지만 다른 세션이 그대로" 상태가 되는데, 이건 다음에
사용자가 비번 변경을 재시도하면 해결되고, 원래 시나리오보다는 훨씬 덜 혼란스러움.

#### 방안 B: 세션 무효화도 outbox로 분리

과도한 개선. UX 이슈 수준이라 방안 A로 충분.

**권장**: **방안 A**.

---

## 🟢 Minor: `SocialSignupController` — 세션 principal stale 문제

### 파일 위치

`src/main/java/com/moongcheap_backend/auth/presentation/SocialSignupController.java:29-36`

### 문제 코드

```java

@PostMapping("/complete")
public ResponseEntity<Void> complete(
    SessionPrincipal principal,
    @RequestBody @Valid SocialSignupCompleteRequestDto request,
    HttpServletRequest httpRequest) {
    SessionPrincipal refreshed = socialSignupCompleteService.complete(principal.memberId(),
        request);
    // ↑ 여기서 서비스 @Transactional 완료 (DB 커밋됨)
    sessionManager.bindPrincipal(httpRequest, refreshed, false);
    // ↑ Redis 세션 principal 교체
    return ResponseEntity.noContent().build();
}
```

### 왜 문제인가

- `socialSignupCompleteService.complete(...)`이 `@Transactional` 메서드라 **리턴 시점에 이미 DB 커밋 완료**.
- 그 직후 `sessionManager.bindPrincipal(...)` 실행 전에 SIGKILL 발생 시:
    - **DB 상태**: 회원 가입 완료 (약관 동의, 닉네임 등 반영됨).
    - **Redis 세션**: 아직 옛 principal (미완료 상태) 그대로.

#### 결과 상태

- 사용자가 다음 요청 보낼 때 세션은 "미완료" principal로 보임.
- 실제 DB는 "완료" 상태이지만 프론트/백엔드가 세션 기반으로 판단하면 "약관 동의 미완료" 화면을 다시 보여줌.
- 사용자가 재시도 시:
    - `socialSignupCompleteService.complete(...)`가 idempotent하다면: 이미 완료된 회원에 대해 조용히 성공 처리 → 세션도 정상
      교체.
    - Idempotent하지 않다면: "이미 가입 완료된 회원입니다" 에러 → 사용자는 로그아웃 후 재로그인 필요.

### 위험도

**🟢 Low**

- 로그아웃 후 재로그인 시 세션이 새로 발급되므로 자동 복구 가능.
- 사용자 조치로 해결 가능한 경미한 UX 이슈.

### 개선 방향

- `socialSignupCompleteService.complete`가 이미 완료된 회원에 대해 idempotent하게 동작하는지 확인 필요.
- 또는 컨트롤러에서 세션 principal을 매 요청마다 DB에서 재조회하는 방식:
  ```java
  SessionPrincipal current = memberQueryService.getPrincipal(principal.memberId());
  ```
- 궁극적으로 세션 principal을 "DB 상태의 캐시"로만 취급하고 캐시 무효화 정책을 명확히 하는 것이 이상적.

---

## 🟢 Minor: `OAuth2LoginSuccessHandler` — Google access token 유실

### 파일 위치

`src/main/java/com/moongcheap_backend/auth/infrastructure/oauth/OAuth2LoginSuccessHandler.java:40-44`

### 문제 코드

```java
sessionManager.bindPrincipal(request, principal, false);          // (a)

if(googleAccessToken !=null){
    request.

getSession().

setAttribute(GOOGLE_ACCESS_TOKEN_ATTR, googleAccessToken);  // (b)
}
```

### 왜 문제인가

- (a) 세션 바인딩 성공 → Redis에 세션 생성 완료.
- (b) 실행 전에 SIGKILL 발생 시:
    - 세션은 존재하지만 `GOOGLE_ACCESS_TOKEN_ATTR`이 빠진 상태.
- 나중에 `WithdrawService.currentGoogleAccessToken()`이 이 토큰을 참조하는데, 없으면 Google revoke 호출을 스킵.
- 결과적으로 사용자 탈퇴 시 **Google 계정과의 앱 연결이 남아있음** (사용자가 수동으로 Google 계정 설정에서 앱 연결 해제해야 함).

### 위험도

**🟢 Low**

- 사용자가 다음에 Google로 로그인하면 새 토큰이 발급되어 세션에 저장 → 자동 복구.
- 사용자가 즉시 탈퇴하지 않는 이상 실질적 영향 없음.
- 심각한 불이익은 없으나 "완벽하지 않은 정리"라는 관찰 포인트.

### 개선 방향

- 두 setAttribute를 원자 그룹으로 묶기 어려우므로 큰 개선은 필요 없음.
- 필요 시 Google access token을 별도 DB 테이블에 저장하여 세션에 의존하지 않도록 리팩터.

---

## 조사했으나 문제 없는 케이스

다음은 초기 검토에서 의심되었으나 실제로 안전한 것으로 확인된 코드입니다.

| 파일                                                 | 초기 의심                             | 실제 결론                                                                              |
|----------------------------------------------------|-----------------------------------|------------------------------------------------------------------------------------|
| `ShippingAddressService.create()`                  | Advisory lock 해제 실패               | `pg_advisory_xact_lock`은 트랜잭션 스코프. SIGKILL 시 PostgreSQL이 연결 유실을 감지해 자동 해제. **안전**  |
| `PaymentPreparationService.schedule()`             | `saveAndFlush` + outbox `save` 분리 | 동일 `@Transactional(REQUIRES_NEW)` 내부에서 원자적으로 커밋. **안전**                            |
| `CustomerKeyIssueTransactionService.getOrCreate()` | 동시 생성 경쟁                          | `findByIdAndDeletedAtIsNullForUpdate` (pessimistic lock) + double-check 패턴. **안전** |
| `ProductService.index/indexAll/delete`             | OpenSearch 반영 실패                  | 관리자 API에서만 호출. 실패 시 재호출 가능. **안전**                                                 |
| `SocialLinkService.unlink()`                       | 외부 언링크 없음                         | 순수 DB 작업만. **안전**                                                                  |

---

## 우선순위 요약

| 순위 | 항목                                | 위험도         | 조치                 |
|----|-----------------------------------|-------------|--------------------|
| 1  | `WithdrawService` 외부 언링크 순서       | 🔴 Critical | Outbox 패턴 도입       |
| 2  | OAuth 로그인 실패 WARN 로그 추가           | 🔴 관찰성      | 각 실패 케이스별 로그 추가    |
| 3  | `PaymentRecoveryScheduler` 커서 손실  | 🟡 Medium   | DB 커서 테이블          |
| 4  | `PasswordChangeService` 세션 순서     | 🟡 Medium   | `afterCommit`으로 이동 |
| 5  | `SocialSignupController` 세션 stale | 🟢 Low      | Idempotency 확인     |
| 6  | `OAuth2LoginSuccessHandler` 토큰 유실 | 🟢 Low      | 다음 로그인에서 복구        |
