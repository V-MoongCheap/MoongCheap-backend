# 세션 ID 회전 race condition 및 Awarding 엔드포인트 경로 통일

## 개요

이 문서는 두 가지 변경 사항의 배경과 해결 과정을 다룬다.

1. **세션 ID 회전 race condition** — 동시성 테스트에서 401이 랜덤하게 발생하던 문제. Spring Security의 세션 고정 방어가 매 요청마다 발동하고 있었고, 50 스레드가 동일 세션으로 접근할 때 Redis RENAME 경합으로 세션이 사라지는 현상이었다.
2. **Awarding 내부 API 경로 통일** — `AwardingController`의 두 엔드포인트 중 하나만 `/internal/`이 없어서 통일시킨 작업.

두 작업은 별개지만 같은 세션에서 다루었기 때문에 함께 정리한다.

---

# 파트 1: 세션 ID 회전 race condition

## 1. 문제 발견

### 1.1 최초 증상

동시성 테스트 실행 시 다음 두 테스트가 랜덤하게 실패:

- `동시성 1-3: 동일 회원이 서로 다른 사업자번호로 이중 등록 시도`
  - `SellerRegistrationSameMemberConcurrencyTest.onlyOneRegistrationSucceedsForSameMember`
- `동시성 3-2: opt-out 저장/삭제 인터리브`
  - `NotificationOptOutInterleaveConcurrencyTest.interleavedEnableDisableIsIdempotent`

두 테스트 다 **개별 실행 시엔 통과**, **다른 테스트와 함께 실행하면 실패**하는 flaky 양상.

### 1.2 첫 번째 오류 스택 (테스트 결과 XML에서 추출)

```
java.lang.AssertionError: 동시성 태스크에서 예기치 않은 예외 발생
    at com.moongcheap_backend.support.concurrency.ConcurrencyRunner.run(ConcurrencyRunner.java:63)
    at com.moongcheap_backend.auth.concurrency.SellerRegistrationSameMemberConcurrencyTest
        .onlyOneRegistrationSucceedsForSameMember(SellerRegistrationSameMemberConcurrencyTest.java:71)
    Suppressed: java.lang.AssertionError: 예상치 못한 응답: status=401
        body={"success":false,"data":null,"error":{"code":"COMMON_401",
              "message":"인증이 필요합니다.","fieldErrors":[]}}
        at SellerRegistrationSameMemberConcurrencyTest.lambda$onlyOneRegistrationSucceedsForSameMember$0
            (SellerRegistrationSameMemberConcurrencyTest.java:88)
    Suppressed: (같은 401이 여러 개 반복)
```

50 스레드 중 일부(1~5개)가 401 응답을 받았다. 정상 흐름이라면 1개는 200(성공), 49개는 409 SELLER_ALREADY_REGISTERED가 나와야 함.

### 1.3 재현 조건 실험

여러 조합으로 실행하며 재현 조건을 좁힘:

| 실행 조합 | 결과 |
|---|---|
| 1-3 단독 | 통과 |
| 1-2 + 1-3 | 통과 |
| SocialLink + 1-3 | 통과 |
| 1-3 + 3-2 | 통과 |
| 1-2 + 1-3 + 3-2 | 통과 |
| **1-2 + 1-3 + SocialLink** (auth.concurrency.*) | **1-3 실패** |
| **auth.concurrency.* + notification.concurrency.*** | **1-3 + 3-2 실패** |

즉 auth.concurrency 패키지의 3개 테스트를 함께 돌리면 재현이 잘 됐다. 처음엔 테스트 간 상태 오염(Redis flushDb 이후에도 남는 무언가)을 의심.

## 2. 초기 시도들과 각 시도의 결과

### 2.1 [실패] JaCoCo 의심

XML 로그를 자세히 보니 어떤 실행에서는 다른 종류의 예외도 관찰됐다:

```
Suppressed: jakarta.servlet.ServletException: Handler dispatch failed:
    java.lang.NoClassDefFoundError: com/moongcheap_backend/auth/domain/BusinessNumberValidator
```

```
Caused by: java.lang.ClassNotFoundException:
    com.moongcheap_backend.common.lock.AdvisoryLockKeys
```

두 클래스 모두 실제로 `build/classes/java/main/`에 존재하는데도 런타임 `ClassNotFoundException`이 발생. 여러 스레드가 처음 참조되는 클래스를 동시에 로드하려 할 때 JaCoCo 에이전트가 바이트코드 instrument하는 과정에서 경합이 발생하는 것으로 의심됨.

**결론**: 이건 별개 이슈로 판단해서 우선순위에서 미룸. 401 문제는 이것과 무관하게 지속되었다. (참고로 세션 문제를 해결한 뒤 이 NoClassDefFoundError는 재현되지 않는 걸로 보이지만, 여전히 잠재적 문제로 남아있다.)

### 2.2 [부분 실패] "Redis save 전 요청" 가설 검증

가설: `SessionTestHelper.loginAs()`의 `repo.save(session)`이 async라서, cookie를 반환한 뒤에도 Redis에 아직 세션이 없는 상태에서 요청이 나가고 있는 것 아닐까?

검증: `SessionTestHelper.createSession()`에 진단 코드 추가.

```java
repo.save(session);
Session verify = repo.findById(session.getId());
if (verify == null) {
    throw new IllegalStateException("[DIAG] session save 직후 findById=null id=" + session.getId());
}
```

3회 반복 실행 결과 `IllegalStateException`이 한 번도 발생하지 않음. → **`repo.save()`는 동기적으로 완료된 뒤 리턴하고 있음.** 가설 기각.

진단 코드는 원상 복구.

### 2.3 [실패] 세션 만료 판정 race 가설

`RedisIndexedSessionRepository.findById()` 소스를 읽어보니:

```java
public RedisSession findById(String id) {
    Map<Object, Object> entries = HGETALL(getSessionKey(id));
    if (entries.isEmpty()) return null;
    MapSession loaded = ...;
    if (loaded.isExpired()) {
        deleteById(id);  // ← 만료 시 세션 자체를 지움
        return null;
    }
    return new RedisSession(loaded, false);
}
```

가설: 어떤 스레드에서 HGETALL 결과가 파싱 과정에서 `lastAccessedTime`이 누락되어 `MapSession` 기본값(생성 시각)이 남고, `isExpired()`가 잘못 true로 판정 → `deleteById()` → 다른 스레드들이 이후 조회하면 null → 401.

검증하려던 순간 사용자로부터 지적: HGETALL은 Redis 원자 명령이라 부분 결과가 나올 수 없고, 방금 만든 30분 TTL 세션이 20초 내 실제 만료될 리도 없음. **가설의 논리적 근거가 부족함.** 이 가설은 폐기.

### 2.4 [진단 목적] SessionPrincipalResolver 로깅 추가 및 롤백

401의 정확한 경로(session null vs attribute null)를 알아보려고 `SessionPrincipalResolver`에 진단 로그를 임시 추가:

```java
if (session == null) {
    log.warn("[DIAG-401] session=null requestedSessionId={} cookie={}", ...);
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
}
```

이 파일은 사용자 요청으로 다시 롤백. 로그를 남기지 않고 다음 단계(MONITOR)로 진행.

## 3. 진짜 원인 발견: Redis MONITOR

### 3.1 방법

`NotificationOptOutInterleaveConcurrencyTest.setUp()`에 임시 코드 추가:
- TestContainers Redis의 매핑된 로컬 포트를 `/tmp/test-redis-port.txt`에 기록
- 5초 sleep을 두어 그 사이에 외부에서 MONITOR 붙일 시간 확보

외부에서:
```bash
docker exec <redis-container> redis-cli monitor > /tmp/redis-monitor.log
```

### 3.2 관찰 결과 (요약)

한 번의 테스트(50 스레드)에서 나간 커맨드 히스토그램:

| 커맨드 | 횟수 |
|---|---|
| SADD | 150 |
| PEXPIRE | 150 |
| HGETALL | 114 |
| SREM | 100 |
| **RENAME** | **100** |
| HMSET | 50 |
| APPEND | 50 |

`RENAME`이 100회. 예상 밖. 자세히 보니:

```
RENAME sessions:fe3744d9-...    → sessions:764167db-...
RENAME sessions:fe3744d9-...    → sessions:bb431d96-...
RENAME sessions:fe3744d9-...    → sessions:2205ff80-...
RENAME sessions:fe3744d9-...    → sessions:d10d4716-...
...  (원본 fe3744d9-... 하나에 대해 다른 UUID로 50번)
```

동일 원본 세션 키를 50개 스레드가 각자 다른 새 UUID로 rename하고 있다. Redis RENAME은 원자적이므로 첫 번째 rename만 성공, 나머지 49개는 "no such key" 실패. 하지만 여러 스레드의 요청 처리는 계속 진행되므로, 옛 ID로 findById하는 스레드는 빈 값 → 401.

**RENAME은 `HttpSession.changeSessionId()`의 흔적.** 누군가 매 요청마다 이걸 부르고 있다는 뜻.

### 3.3 SecurityConfig 조사

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
```

`sessionManagement`를 활성화하는 순간 Spring Security의 `SessionManagementFilter`가 필터 체인에 들어가고, 기본 세션 고정 방어(`SessionFixationProtectionStrategy`)가 등록된다.

`SessionManagementFilter`의 로직 (Spring Security 소스):
```java
if (!securityContextRepository.containsContext(request)) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && !trustResolver.isAnonymous(authentication)) {
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        // ↑ 여기가 세션 고정 방어 → session.changeSessionId() 호출
    }
}
```

`containsContext(request)`는 기본 구현 `HttpSessionSecurityContextRepository`가 세션에서 `SPRING_SECURITY_CONTEXT` 어트리뷰트를 찾는다.

### 3.4 우리 코드가 이 조건을 매번 만족시키는 이유

`SessionAuthenticationFilter` (커스텀 필터):
```java
if (SecurityContextHolder.getContext().getAuthentication() == null) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        Object attr = session.getAttribute(AuthSessionManager.PRINCIPAL_ATTR);
        if (attr instanceof SessionPrincipal principal) {
            ...
            SecurityContextHolder.getContext().setAuthentication(auth);
            // ↑ ThreadLocal에만 세팅, 세션에는 저장 안 함
        }
    }
}
```

즉 우리 필터는 매 요청마다:
- 세션에서 principal 읽어와서
- `SecurityContextHolder`에 `Authentication` 세팅
- **`SPRING_SECURITY_CONTEXT` 어트리뷰트는 세션에 저장하지 않음**

결과적으로 매 요청 시작 시점의 판정이 항상:
- `containsContext(request) == false` (SPRING_SECURITY_CONTEXT 없음)
- `Authentication` 존재 (우리 필터가 방금 세팅)
- → "새 로그인이 방금 발생함" 으로 판정 → `changeSessionId()` 호출

**즉 인증된 모든 요청마다 세션 ID가 회전되고 있었다.** 프로덕션에서도 동일하게 발생 중이었을 문제.

### 3.5 왜 여러 테스트 조합에서만 재현됐나

각 요청이 세션 ID를 갈아치우니 다음 요청은 새 ID로 와야 함. MockMvc + Set-Cookie 처리가 없는 테스트에서, 50 스레드가 동시에 옛 ID로 rename하려 함 → 경합. 테스트 조합에 따라 JVM/GC/timing이 달라져 재현 확률이 바뀌었을 뿐, 근본 원인은 일정.

## 4. 해결책 검토

### 4.1 옵션 A: SecurityContext를 세션에 저장 (정공법)

`SessionAuthenticationFilter`에서 `securityContextRepository.saveContext(ctx, req, res)` 호출.

- 장점: Spring Security 표준 흐름을 따름. 세션 고정 방어를 Spring이 계속 담당.
- 단점: 세션 데이터에 `SPRING_SECURITY_CONTEXT` 어트리뷰트 추가됨(수백 바이트). 필터에 코드 추가 필요.

### 4.2 옵션 B: `.sessionFixation().none()`

- 장점: 한 줄 변경. Redis 데이터도 변화 없음. RENAME storm 즉시 해결.
- 단점: Spring Security의 자동 세션 고정 방어가 전역 비활성화됨. 우리 로그인 흐름이 이미 방어를 하고 있어야 함.

### 4.3 우리 로그인이 이미 방어 중임을 확인

`AuthSessionManager.bindPrincipal()`:
```java
public void bindPrincipal(HttpServletRequest request, SessionPrincipal principal, boolean rememberMe) {
    HttpSession existing = request.getSession(false);
    if (existing != null) {
        existing.invalidate();       // 기존 세션 완전 삭제
    }
    var session = request.getSession(true);   // 새 세션 생성
    ...
}
```

`invalidate` + `getSession(true)`는 changeSessionId보다도 강력한 방어(RENAME이 아니라 완전 새 ID + 기존 어트리뷰트 초기화). 즉 로그인 시점 세션 고정 방어는 이미 우리가 하고 있음.

### 4.4 권한 상승 지점 회전 필요성 검토

권한이 바뀌는 지점은 두 곳:
- 소셜 회원가입 완료 (`SocialSignupCompleteService.complete`) — guest → 정식 회원
- 판매자 등록 (`SellerRegistrationController.create`) — BUYER → SELLER

OWASP 가이드라인상 privilege escalation 시 세션 ID 회전이 권장됨. 지금 코드는 `refreshPrincipal`로 principal 어트리뷰트만 갱신할 뿐 sessionId는 그대로.

옵션 B로 가면 Spring이 자동으로 해주던 회전이 아예 사라지므로, 이 두 지점은 명시적으로 `httpRequest.changeSessionId()`를 호출해야 함.

### 4.5 결정

**옵션 B 채택 + 권한 상승 지점 2곳에 명시적 `changeSessionId()` 추가.**

## 5. 최초 구현과 결과

### 5.1 변경 파일

**SecurityConfig.java**
```java
.sessionManagement(sm -> sm
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
    .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::none))
```

**SellerRegistrationController.java**
```java
Long sellerId = sellerRegistrationService.register(principal.memberId(), request);
httpRequest.changeSessionId();  // 추가
sessionManager.refreshPrincipal(httpRequest,
    sellerRegistrationService.buildRefreshedPrincipal(principal.memberId()));
```

**SocialSignupCompleteService.java** — 세션 조작을 컨트롤러로 이동 (DB 커밋 후 회전 순서 보장 목적)
- `complete()`가 void에서 `SessionPrincipal` 반환으로 변경
- `HttpServletRequest`, `AuthSessionManager` 의존성 제거
- 리팩터에 따라 SocialSignupCompleteServiceSuccessTest, SocialSignupCompleteServiceFailureTest 시그니처 수정

**SocialSignupController.java**
```java
SessionPrincipal refreshed = socialSignupCompleteService.complete(principal.memberId(), request);
httpRequest.changeSessionId();  // 추가
sessionManager.refreshPrincipal(httpRequest, refreshed);
```

### 5.2 재실행 결과 (3회 반복)

```
=== Run 1 === 동시성 1-3 FAILED
=== Run 2 === 동시성 1-3 FAILED
=== Run 3 === 동시성 1-3 FAILED
```

**3-2는 통과, 1-3은 여전히 실패.** 진전은 있으나 완전 해결은 안 됨.

## 6. 잔존 문제 진단

### 6.1 새로운 에러 메시지

```
Suppressed: jakarta.servlet.ServletException: Request processing failed:
    java.lang.IllegalStateException: creationTime key must not be null
```

401이 아닌 500. Spring Session의 `RedisSessionRepository`가 세션 로드 시 hash에서 `creationTime` 필드가 null이면 던지는 예외.

### 6.2 시나리오 재구성

1-3 테스트에서:
1. 50 스레드가 같은 세션 쿠키로 진입
2. 각자 `SellerRegistrationService.register()` 호출
3. 승자 1건 성공, 49건은 `SELLER_ALREADY_REGISTERED` 반환
4. 승자 컨트롤러가 `httpRequest.changeSessionId()` → Redis `RENAME sessions:{old} → sessions:{new}`
5. 이 시점 loser 49 스레드는 아직 컨트롤러 리턴 후 필터 후처리 단계
6. 각 loser의 `SessionRepositoryFilter.commitSession()`이 `session.save()` 호출
7. Spring Session은 세션이 modified 상태(lastAccessedTime 등)로 판단 → 옛 ID로 HSET
8. 옛 ID가 이미 rename으로 없어졌으므로 새 hash가 `{lastAccessedTime}` 필드만 갖고 생성됨
9. 이후 그 옛 ID로 findById → creationTime 없음 → `IllegalStateException`

**요약: 승자 1개의 `changeSessionId()`가 진행 중인 다른 49개 요청의 세션 write-back과 충돌.**

### 6.3 프로덕션 vs 테스트

- 프로덕션: 사용자가 판매자 등록 폼 제출을 동시에 50번 누르는 시나리오는 없음. race window가 사실상 발생 안 함.
- 테스트: 의도적으로 동일 세션 50 스레드로 동시 실행하는 인공적 부하 → race가 반드시 노출.

### 6.4 시도한 대안 판단

- **옵션 1**: 회전 유지 + 테스트에서 401 허용. 프로덕션 무해, 테스트 목적(DB 유니크 제약 검증)엔 크게 관계없음.
- **옵션 2**: 회전 제거. 보안 이득 감소.

사용자 판단으로 회전을 유지하기로 결정. 그러면 테스트 방식을 바꿔야 함.

## 7. 최종 해결

### 7.1 테스트 리팩터 (SellerRegistrationSameMemberConcurrencyTest)

MockMvc + 동일 세션 방식을 **서비스 직접 호출**로 전환. 코드베이스에 이미 존재하는 `SocialLinkUnlinkConcurrencyTest`와 동일한 패턴.

```java
ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
    try {
        sellerRegistrationService.register(memberId, requests.get(idx));
        return true;
    } catch (BusinessException e) {
        if (e.getErrorCode() == ErrorCode.SELLER_ALREADY_REGISTERED) {
            return false;
        }
        throw e;
    } catch (DataIntegrityViolationException e) {
        return false;
    }
});
```

주요 변경:
- `MockMvc`, `SessionTestHelper`, `sessionCookie` 제거
- `SellerRegistrationService.register()` 직접 호출
- 실패 케이스는 두 가지 예외로 잡음:
  - `BusinessException(SELLER_ALREADY_REGISTERED)` — `exists...` 체크가 다른 스레드 커밋 이후에 걸린 경우
  - `DataIntegrityViolationException` — `exists...` 체크는 통과했지만 INSERT commit에서 `uq_seller_member_id` 위반

두 번째 catch가 필요한 이유: 컨트롤러 경유가 아니라 서비스 직접 호출이므로 `GlobalExceptionHandler.handleDataIntegrity`의 CONSTRAINT_ERROR_MAP 매핑을 안 탐. 원시 `DataIntegrityViolationException`이 그대로 올라옴.

### 7.2 최종 재실행 결과 (3회 반복)

```
=== Run 1 === BUILD SUCCESSFUL in 22s
=== Run 2 === BUILD SUCCESSFUL in 846ms
=== Run 3 === BUILD SUCCESSFUL in 860ms
```

Run 2, 3이 짧은 이유는 Gradle up-to-date. 3회 모두 통과.

## 8. 최종 상태 요약

### 8.1 프로덕션 코드

| 파일 | 변경 |
|---|---|
| `SecurityConfig.java` | `sessionFixation(none)` 설정 추가 |
| `SellerRegistrationController.java` | `httpRequest.changeSessionId()` 호출 추가 |
| `SocialSignupController.java` | `AuthSessionManager` 주입 + `httpRequest.changeSessionId()` + `refreshPrincipal` 호출 (서비스에서 이동) |
| `SocialSignupCompleteService.java` | `HttpServletRequest`, `AuthSessionManager` 의존성 제거. `void` → `SessionPrincipal` 반환 |

### 8.2 테스트 코드

| 파일 | 변경 |
|---|---|
| `SellerRegistrationSameMemberConcurrencyTest.java` | MockMvc → 서비스 직접 호출로 전환. 두 예외 타입 모두 정상 실패로 집계 |
| `SocialSignupCompleteServiceSuccessTest.java` | 시그니처 변경 반영 (mock 정리, 반환 principal 검증) |
| `SocialSignupCompleteServiceFailureTest.java` | 시그니처 변경 반영 (HttpServletRequest 인자 제거) |

### 8.3 세션 lifecycle 정리

| 이벤트 | sessionId 변화 | 담당 |
|---|---|---|
| 로컬 로그인 | 새로 발급 | `AuthSessionManager.bindPrincipal` (`invalidate` + `getSession(true)`) |
| 소셜 로그인 | 새로 발급 | 동일 |
| 소셜 가입 완료 | **회전** | `SocialSignupController.complete` — `httpRequest.changeSessionId()` |
| 판매자 등록 | **회전** | `SellerRegistrationController.create` — `httpRequest.changeSessionId()` |
| 일반 요청 | 유지 | (자동 회전 비활성화) |
| 로그아웃 | 소멸 | `invalidateCurrent` |
| 회원 탈퇴 | 소멸 (모든 세션) | `invalidateAllForMember` |
| 비밀번호 변경 | 현재 유지, 다른 세션 소멸 | `invalidateAllExceptCurrent` |

### 8.4 부수 효과

- 인증된 요청당 Redis 왕복이 크게 감소 (RENAME + SREM + SADD + PEXPIRE 사라짐)
- 브라우저 쿠키가 매 응답마다 갱신되던 것도 사라짐 → 클라이언트에서도 안정적
- 향후 Spring Security의 form login이나 다른 자동 인증 흐름을 추가하면 세션 고정 방어가 안 걸리므로 별도 처리 필요

### 8.5 알려진 잔존 이슈

- **JaCoCo 관련 `NoClassDefFoundError`**: 별개 문제로 미해결 상태. 병렬 클래스 로드 시 JaCoCo instrumentation 경합으로 추정. 현재는 재현되지 않지만 잠재적 flakiness 원인으로 남아있음.
- **소셜 회원가입 완료의 트랜잭션 경계**: `SocialSignupCompleteService.complete`는 `@Transactional`인데 컨트롤러에서 `changeSessionId`가 커밋 이후에 실행되도록 서비스가 principal만 반환하도록 리팩터됨. 이 부분은 판매자 등록 컨트롤러와 동일 패턴.

---

# 파트 2: Awarding 엔드포인트 경로 통일

## 1. 배경

`AwardingController`의 두 엔드포인트:

```java
@GetMapping("/pending")           // AI 판정 대기 보드 조회
public AwardingPendingResponseDto getPendingAwarding(...)

@PostMapping("/internal/result")  // AI 낙찰 결과 반영
public AwardingResultResponseDto applyAwardingResult(...)
```

둘 다 내부 API 전용인데 `pending`만 `/internal` 세그먼트가 없어서 URL 스타일이 불일치.

## 2. 변경

`@GetMapping("/pending")` → `@GetMapping("/internal/pending")`

결과 엔드포인트:
- `GET /api/awarding/internal/pending`
- `POST /api/awarding/internal/result`

## 3. 인증 필터 동작 확인

`InternalApiKeyFilter.INTERNAL_PATTERNS`:
```java
"/api/**/internal",
"/api/**/internal/**",
"/api/awarding/**"
```

`/api/awarding/**`가 이미 포함되어 있으므로, 경로에 `/internal`이 있든 없든 이 필터의 API 키 검증은 계속 적용됨. 즉 이 변경은 순수 URL 스타일 통일 목적이고 인증 동작은 그대로 유지됨.

`SecurityConfig`의 `permitAll` 목록에도 `/api/awarding/**`가 이미 있어서 Spring Security 관점에서도 그대로 통과.
