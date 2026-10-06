# 회원 탈퇴 시 Provider Unlink Outbox 전환

작성일: 2026-09-30

상태: 구현 완료. `V25__add_pending_provider_unlink_and_refresh_token.sql`, `WithdrawService`, `ProviderUnlinkWorker`, `ProviderUnlinkChunkService`, `PendingProviderUnlink`로 반영되어 운영 코드에 적용되어 있다.

## 1. 배경

회원 탈퇴(`WithdrawService#withdraw`)는 아래 작업을 한 번의 사용자 요청 안에서 수행한다.

- 로컬/소셜 자격증명 검증
- 탈퇴 가능 여부 확인 (`WithdrawEligibilityChecker`)
- 배송지, 알림 opt-out, 소셜 자격증명, 로컬 자격증명 삭제
- `Member#withdraw` 로 도메인 상태 전환 (`deleted_at`)
- 카카오·구글 등 소셜 provider 계정 연결 해제 (unlink / revoke)
- 세션 무효화 (커밋 이후 이벤트)

이 중 **소셜 provider unlink** 만 외부(카카오·구글) HTTP 호출이다. 나머지는 전부 자체 DB 작업이다.

## 2. 문제: 이전 방식과 그 위험

이전 구현은 트랜잭션 안에서 provider unlink API 를 곧바로 호출했다. 구조는 대략 다음과 같다.

```java
@Transactional
public void withdraw(...) {
    // 1. 검증 및 도메인 조회
    // 2. shippingAddress / optOut / socialCredential / localCredential 삭제
    // 3. member.withdraw();
    // 4. kakaoOAuth2Client.unlink(providerId);     // ← 외부 HTTP
    //    googleOAuth2Client.revoke(refreshToken);  // ← 외부 HTTP
    // 5. eventPublisher.publishEvent(...);
}
```

이 구조에는 아래와 같은 문제가 있다.

### 2.1 외부 성공 · 내부 실패 발생 시 복구 불가 (사용자가 지적한 주된 이유)

`kakaoOAuth2Client.unlink(...)` 가 200을 받고 리턴한 뒤, 트랜잭션 커밋 단계에서 실패하는 경우가 존재한다. 예를 들어:

- 커밋 시점에 DB 연결이 끊긴다.
- Serialization / lock 관련 예외가 마지막에 터진다.
- 커밋 직전 pod 가 SIGTERM 을 받아 강제 종료된다.

이때 결과는:

- 외부 provider: 이미 unlink 완료 (되돌릴 수 없다).
- 내부 DB: 롤백. `member.deleted_at` 은 null, `social_credential` 은 그대로 남는다.

즉 **사용자는 여전히 활성 회원인데 소셜 로그인만 못 하는 상태**가 된다. 재로그인은 실패하고, 관리자가 개입하지 않으면 복구가 불가능하다. 외부 side effect 는 롤백되지 않는다는 근본 문제다.

### 2.2 반대로 내부 성공 · 외부 실패 시 unlink 유실

트랜잭션 안에서 외부 호출이 예외를 던지면 트랜잭션 전체가 롤백된다. 이 경우 회원은 탈퇴 실패로 응답을 받고 재시도할 수 있어 표면상 문제는 없다. 그러나 사용자 UX 측면에서 다음 두 가지가 겹친다.

- 카카오/구글 장애가 5분만 지속되어도 그 사이 모든 탈퇴가 실패한다.
- 재시도 시에도 같은 장애가 이어지면 사용자는 탈퇴 자체를 포기한다.

또한 여러 provider 가 연결된 계정(예: 카카오 + 구글 동시 연결)이라면, **첫 번째 provider 는 unlink 성공했는데 두 번째 provider 호출에서 실패**하는 부분 성공이 발생한다. 이 경우:

- 트랜잭션은 롤백되지만 이미 카카오 계정에서는 앱 연결이 해제되어 있다.
- 다음 재시도에서 다시 카카오 unlink 를 호출하지만, 이미 해제된 상태이므로 provider 응답이 달라질 수 있다.
- 응답에 따라 워커가 실패로 인지하면 무한 재시도 루프에 빠질 수 있다.

### 2.3 트랜잭션 안에서 외부 I/O 를 잡는 안티패턴

외부 HTTP 호출은 수백 ms ~ 수 초 소요될 수 있다. 그동안:

- HikariCP 커넥션을 계속 점유한다. 커넥션 풀이 20 개인데 탈퇴 요청 20 개가 동시에 들어와 모두 카카오 응답을 기다리면, 전 서비스가 커넥션 부족으로 5xx 를 낸다.
- `member`, `local_credential`, `social_credential`, `shipping_address` 행에 잡힌 락이 그대로 유지된다. 같은 사용자 관련 다른 트랜잭션(예: 관리 조회, 로그아웃)이 락 대기로 밀린다.
- `idle_in_transaction_session_timeout = 60000` (`application.yml:11`) 설정에 걸려 커넥션이 강제로 끊길 수 있다.

이는 이 레포의 다른 도메인이 이미 지키고 있는 원칙(`brandpay-postgresql-queue-design.md`, `ProviderUnlinkChunkService` 주석)과 정면으로 어긋난다.

### 2.4 응답 지연

사용자 입장에서 탈퇴 버튼을 눌렀을 때 응답까지의 시간이 provider 응답 지연에 그대로 노출된다. 카카오/구글 응답이 3초 걸리면 탈퇴 응답도 3초 이상이다. 두 provider 순차 호출이면 두 배가 된다.

### 2.5 재시도 로직을 넣기 어려움

트랜잭션 안에서 재시도 루프를 돌리면 트랜잭션 시간이 배가되어 위 2.3 문제를 악화시킨다. 반대로 재시도 없이 실패를 그대로 예외로 던지면 provider 잠깐의 장애에도 탈퇴가 실패한다.

### 2.6 Refresh token 유실

구글은 unlink 가 아니라 refresh_token 을 revoke 하는 방식이다. 즉시 호출에 실패해서 재시도하려 해도, 회원 데이터가 이미 삭제되었다면 refresh_token 을 다시 조회할 방법이 없다. 즉 트랜잭션 롤백 없이 재시도하려면 revoke 에 필요한 자료를 별도로 보관해야 한다.

## 3. 해결 방안: Outbox 패턴

외부 호출을 **로컬 DB 에 "할 일" 로 기록** 하고 (같은 트랜잭션 안에 커밋), 워커가 그 기록을 소비해 실제 외부 호출을 담당한다. 이렇게 하면:

- 트랜잭션 안에서 외부 I/O 를 잡지 않는다.
- 트랜잭션이 커밋됐다면 unlink 요청 기록도 반드시 남는다 (원자성).
- 트랜잭션이 롤백됐다면 unlink 요청 기록도 없다.
- 외부 실패는 워커가 재시도 · dead letter 로 흡수한다.
- 사용자 응답은 provider 응답을 기다리지 않는다.

이 문서의 대상 구현은 `pending_provider_unlink` 테이블과 `ProviderUnlinkWorker` 워커다.

### 3.1 스키마

`V25__add_pending_provider_unlink_and_refresh_token.sql`:

```sql
ALTER TABLE member_social
    ADD COLUMN refresh_token_enc TEXT;  -- 구글 revoke 재시도를 위해 회원 데이터와 별도로 보관

CREATE TABLE pending_provider_unlink (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    revocation_token_enc TEXT,               -- 구글: 암호화된 refresh_token, 카카오: null
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,      -- 이 시각 이후에만 워커가 잡음
    dead_lettered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pending_provider_unlink_next_attempt
    ON pending_provider_unlink (dead_lettered, next_attempt_at);
```

특히 `revocation_token_enc` 는 구글 revoke 재시도를 위해 refresh_token 을 회원 삭제 이후에도 보존하려고 outbox row 안으로 복사한다. `social_credential` 이 지워져도 워커가 revoke 를 계속 시도할 수 있다.

### 3.2 write path: `WithdrawService`

```java
List<SocialCredential> socials = socialCredentialRepository.findAllByMemberId(memberId);
LocalDateTime now = LocalDateTime.now();
for (SocialCredential cred : socials) {
    pendingProviderUnlinkRepository.save(PendingProviderUnlink.of(
        memberId,
        cred.getProvider(),
        cred.getProviderId(),
        cred.getRefreshTokenEnc(),
        now
    ));
}

shippingAddressRepository.deleteAllByMemberId(memberId);
notificationOptOutRepository.deleteAllByMemberId(memberId);
socialCredentialRepository.deleteByMemberId(memberId);
localCredentialRepository.deleteByMemberId(memberId);

member.withdraw();
```

- outbox insert 와 회원 데이터 삭제, `deleted_at` 갱신이 **하나의 트랜잭션** 안에 있다.
- 커밋되면 위 다섯 가지 변화가 모두 반영된다. 롤백되면 모두 반영되지 않는다.
- **외부 호출은 이 트랜잭션 안에 없다.**

### 3.3 read path: `ProviderUnlinkWorker` + `ProviderUnlinkChunkService`

워커는 3-phase (`claim → external call → reconcile`) 로 나뉘어 각각을 짧은 트랜잭션으로 커밋한다.

`ProviderUnlinkChunkService.claim`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
public List<PendingProviderUnlink> claim(int chunkSize, Duration hideDuration) {
    LocalDateTime now = LocalDateTime.now();
    List<PendingProviderUnlink> rows = repository.selectClaimable(now, chunkSize);
    LocalDateTime hideUntil = now.plus(hideDuration);
    rows.forEach(r -> r.hideUntil(hideUntil));
    return rows;
}
```

`selectClaimable` 은 다음 native query 다.

```sql
SELECT * FROM pending_provider_unlink
 WHERE dead_lettered = false
   AND next_attempt_at <= :now
 ORDER BY next_attempt_at ASC
 LIMIT :chunkSize
 FOR UPDATE SKIP LOCKED
```

- `FOR UPDATE SKIP LOCKED` 로 여러 pod / 여러 tick 이 동시에 같은 row 를 잡지 않는다.
- claim 이 커밋되면 `next_attempt_at` 이 미래(`hideDuration`, 기본 5분)로 밀려 다른 워커는 그 사이 다시 잡지 못한다.
- 이 트랜잭션은 짧다. 외부 호출 없이 곧바로 커밋.

이후 워커는 트랜잭션 밖에서 provider 를 호출한다.

```java
private void process(PendingProviderUnlink p) {
    try {
        callProvider(p);
        chunkService.markDone(p.getId());       // 성공 → 별도 트랜잭션에서 삭제
    } catch (Exception e) {
        log.warn("provider unlink failed id={} provider={} providerId={} retryCount={}",
            p.getId(), p.getProvider(), p.getProviderId(), p.getRetryCount(), e);
        chunkService.markFailed(p.getId(), maxRetry);  // 실패 → 별도 트랜잭션에서 재시도 예약
    }
}
```

`markFailed` 는 지수 백오프로 `next_attempt_at` 을 다시 세팅하고, `retry_count > maxRetry` 이면 `dead_lettered = true` 로 큐에서 뺀다.

```java
public void scheduleRetry(LocalDateTime now) {
    this.retryCount++;
    long delaySeconds = Math.min(1L << Math.min(retryCount, 8), MAX_RETRY_DELAY_SECONDS);
    this.nextAttemptAt = now.plusSeconds(delaySeconds);
}
```

- 1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s(cap 300s) 로 늘어난다.
- 최대 재시도 (`maxRetry`, 기본 8) 를 넘으면 dead letter 처리 → 사람이 개입해서 처리한다.

### 3.4 provider 별 호출 분기

```java
private void callProvider(PendingProviderUnlink p) {
    switch (p.getProvider()) {
        case KAKAO -> kakaoOAuth2Client.unlink(p.getProviderId());
        case GOOGLE -> {
            String token = encryptionService.decrypt(p.getRevocationTokenEnc());
            googleOAuth2Client.revoke(token);
        }
    }
}
```

- 카카오는 admin key + provider_id 로 unlink → outbox row 만 있으면 재시도 가능.
- 구글은 refresh_token 필요 → outbox row 안에 암호화 상태로 보관.

### 3.5 각 문제에 대한 해결 대응표

| 원인 (2.x) | 해결 |
|---|---|
| 2.1 외부 성공 · 내부 실패 | 외부 호출을 트랜잭션 밖으로 뺐다. outbox 커밋 이후에만 외부 호출한다. |
| 2.2 부분 성공 | provider 별로 outbox row 를 따로 만들고 각각 재시도한다. 한 provider 실패가 다른 provider 를 막지 않는다. |
| 2.3 트랜잭션 안 I/O | claim / call / reconcile 3-phase 로 분리. 각 트랜잭션은 5초 timeout 안에 완료된다. |
| 2.4 응답 지연 | 사용자 응답은 outbox insert 까지만 기다린다. 외부 호출은 백그라운드 워커가 담당. |
| 2.5 재시도 어려움 | outbox row 자체가 재시도 상태를 들고 있어 지수 백오프 + dead letter 를 자연스럽게 표현. |
| 2.6 refresh token 유실 | `revocation_token_enc` 를 outbox row 안에 복사해 회원 삭제 이후에도 유지. |

## 4. 해결 과정 (마이그레이션 순서)

실제 반영은 아래 순서로 진행했다.

1. **문제 인식**: 탈퇴 중 provider 응답 지연으로 커넥션 풀 압박이 관측되었고, 커밋 직전 예외로 인한 외부-내부 불일치 리스크가 코드 리뷰에서 지적되었다.
2. **outbox 스키마 설계** (`V25__add_pending_provider_unlink_and_refresh_token.sql`):
   - `pending_provider_unlink` 테이블 신설
   - `member_social.refresh_token_enc` 컬럼 추가 (구글 revoke 재시도용)
   - `(dead_lettered, next_attempt_at)` 인덱스로 워커 조회 최적화
3. **도메인 엔티티 추가** (`PendingProviderUnlink`):
   - `hideUntil`, `scheduleRetry`, `markDeadLettered` 상태 전이 메서드
   - 지수 백오프 상한(`MAX_RETRY_DELAY_SECONDS = 300`) 상수화
4. **repository** (`PendingProviderUnlinkRepository`):
   - `SKIP LOCKED` native query 로 다중 pod 안전 확보
5. **write path 수정** (`WithdrawService`):
   - 외부 클라이언트 직접 호출 제거
   - `SocialCredential` 목록만큼 outbox insert
   - 삭제·`member.withdraw()`·outbox insert 모두 같은 `@Transactional` 안에 포함
6. **워커 도입** (`ProviderUnlinkWorker`, `ProviderUnlinkChunkService`):
   - `@Scheduled(fixedDelayString = ...)` 로 10초 간격 tick
   - `claim → callProvider → markDone/markFailed` 3-phase
   - 각 phase 는 `REQUIRES_NEW` + `timeout = 5` 로 격리
   - 실패 시 `log.warn` (스택 트레이스 포함, CLAUDE.md 로깅 규칙 준수)
7. **구글 refresh_token 저장 경로 정비**:
   - OAuth2 로그인 시 refresh_token 수신하면 `member_social.refresh_token_enc` 에 암호화 저장
   - 탈퇴 시 이 값을 `pending_provider_unlink.revocation_token_enc` 로 복사
8. **세션 무효화 분리** (`MemberWithdrawnEventListener`):
   - `@TransactionalEventListener(AFTER_COMMIT)` 로 세션 무효화(Redis)를 트랜잭션 밖으로 분리. outbox 원칙과 동일한 사고 (외부 I/O 를 트랜잭션 안에서 잡지 않는다).

## 5. 남은 이슈와 운영 시 유의점

- **at-least-once**: outbox 는 `정확히 한 번` 을 보장하지 않는다. 워커가 provider 호출에 성공한 뒤 `markDone` 직전에 죽으면 다음 tick 에 다시 호출된다. 카카오 unlink · 구글 revoke 는 idempotent 이므로 두 번 호출되어도 결과는 같다.
- **dead letter**: `dead_lettered = true` 로 넘어간 row 는 운영자가 원인 확인 후 수동 처리해야 한다. 알람이 필요하면 `dead_lettered = true` row 수를 주기적으로 집계해 알려야 한다.
- **claim hide duration**: 기본 5분이다. 외부 호출이 이보다 오래 걸리면 다른 pod 가 같은 row 를 다시 잡을 수 있다. provider 별 실제 응답 분포를 확인해 조정한다.
- **워커 지연**: `fixedDelayString` 기본 10초. 사용자 관점에서 unlink 는 탈퇴 후 최대 (10초 + provider 응답 시간) 늦게 반영된다. 이 정도면 UX 문제는 없다고 판단했다.
- **탈퇴 API 응답**: 외부 unlink 결과와 무관하게 200 을 반환한다. "탈퇴 완료" 는 회원 데이터 삭제 기준이며, provider unlink 는 별개 프로세스라는 사실을 팀 내 · 프론트에 공유되어야 한다.
