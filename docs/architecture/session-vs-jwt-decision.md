# 세션 기반 인증 선택 결정 기록

## 요약

대부분의 유스케이스가 로그인 사용자를 전제로 하므로 **로그인 유지 시간을 길게** 가져가고 싶었다. 그러나 긴 유지 시간은 탈취·오남용 리스크를 늘리므로, **"상황별 즉시 차단"**과 **"짧은 범위의 세션 회전"**이 함께 성립해야 했다. 두 조건을 저렴하게 만족하는 쪽은 서버가 상태를 쥐는 **Spring Session + Redis** 였고, 그래서 JWT 액세스 토큰 대신 세션 방식을 선택했다.

| 비교 | Session (Spring Session + Redis) | JWT (액세스 토큰) |
|:---|:---|:---|
| 로그인 유지 시간 | 서버 TTL로 자유 조절 (24h / 14d) | 토큰 만료까지 — 길면 탈취 리스크 ↑ |
| 즉시 차단 | 서버에서 세션 삭제 1회로 종결 | 블랙리스트 저장소 필수 (= 사실상 상태 쥐어야 함) |
| 중간 상태 보관 | 세션 속성에 저장 (ex. `termsAgreed=false`) | 토큰 재발급 필요 |
| 멀티 pod 공유 | Redis 공유로 자연스러움 | 토큰은 공유 필요 없지만, 블랙리스트는 공유 필요 |
| 로그인 유지의 리스크 상쇄 | 권한 상승 지점에서 세션 ID 회전 | 상대적으로 어려움 |

> 결론: JWT의 Stateless 이점은 이 서비스에서 체감 가치가 낮았다. 반대로 **"장기 유지 + 즉시 차단"**이라는 요건에서 세션의 서버 상태 관리가 바로 이득으로 돌아왔다.

---

## 1. 두 가지 설계 목표

### 1-1. 로그인 유지 시간을 길게 가져가고 싶었다

이 서비스의 거의 모든 기능(수요 등록, 공동구매 참여, 결제수단 관리, 주문 조회 등)은 **로그인 사용자 전제**다. 로그인이 자주 끊기면 참여 전환율이 떨어지는 구조라서, 세션은 **길게** 가져가야 했다.

적용된 TTL (`application.yml` · `moongcheap.security.session.*`):

| 종류 | TTL |
|:---|:---|
| 기본 (`absolute-ttl-default`) | **24시간** |
| Remember-Me (`absolute-ttl-remember-me`) | **14일** |

### 1-2. 상황별 즉시 차단

"길게 유지"가 되려면 **문제 상황에서 즉시 끊을 수 있다**는 전제가 필요하다. 즉시 차단이 어려우면 유지 시간을 길게 가져갈 수 없다. 구현된 차단 분기는 다음과 같다.

| 시나리오 | 차단 범위 | 구현 |
|:---|:---|:---|
| 비밀번호 변경 | 현재 세션 유지 + **다른 기기 세션만** 전부 삭제 | `AuthSessionManager.invalidateAllExceptCurrent` (`PasswordChangeService.java:44`) |
| 회원 탈퇴 | **모든 세션** 삭제 (`AFTER_COMMIT` 이벤트로) | `AuthSessionManager.invalidateAllForMember` (`MemberWithdrawnEventListener.java:21`) |
| 로그아웃 | 현재 세션만 삭제 | `AuthSessionManager.invalidateCurrent` |

"탈취 의심 시 다른 기기만 끊고 현재 세션은 유지" 같은 **상태 변경 성격에 따라 다른 범위**를 요구하는 분기가 자연스럽게 성립한다. JWT였다면 토큰별 블랙리스트 조회를 매 요청마다 돌려야 해서 결국 서버가 상태를 쥐게 된다.

---

## 2. 세션을 택한 보조 근거

### 2-1. 소셜 가입 미완(중간 상태) 보관

소셜 로그인은 **약관 동의 전까지 "가입 미완" 중간 상태**를 가진다. 이 플래그를 세션 속성에 담아두고, Spring Security 필터 체인에서 바로 분기 처리한다.

```
OAuth2LoginSuccessHandler
  ├─ SessionPrincipal (termsAgreed=false) 세션 바인딩
  └─ redirect: /oauth/callback?status=incomplete

IncompleteSignupFilter
  └─ termsAgreed=false 인 사용자 요청 차단

POST /api/auth/social-signup/complete
  → 약관 동의 처리
  → bindPrincipal() 로 새 세션 발급 (상태 변경 지점)
```

JWT라면 "약관 동의 전 토큰"과 "완료 후 토큰"을 분리 발급·관리해야 한다. 세션 속성에 플래그 하나 바꾸는 것으로 끝나는 쪽이 명백히 저렴하다.

### 2-2. 멀티 pod 상태 공유

Spring Session Data Redis가 세션을 Redis(`moongcheap:session` 네임스페이스)에 저장한다. 여러 pod가 같은 Redis를 바라보므로 **세션 상태 불일치가 발생하지 않는다**. 수평 확장 시 추가 설계가 필요하지 않다.

JWT는 Stateless라 공유가 필요 없지만, **블랙리스트**(=차단 상태)는 공유해야 하므로 결국 Redis가 필요하다. 그렇다면 세션 자체를 Redis에 두는 쪽이 설계가 단순하다.

---

## 3. 장기 유지의 리스크를 어떻게 상쇄했나 — Session Fixation 방어

장기 유지가 바로 쓰일 수 있는 이유는 **세션 ID가 공격자에게 넘어가도 금방 무효화된다**는 전제가 있어야 한다. 이를 위해 **"권한·상태가 바뀌는 지점에서 세션 ID를 명시적으로 회전"**한다.

### 3-1. 자동 회전은 끔

Spring Security의 자동 session fixation 방어(요청마다 세션 ID 교체)는 **의도적으로 비활성화**했다 (`SecurityConfig.java:53`):

```java
.sessionManagement(sm -> sm
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
    // 세션 재발급은 로그인/권한 변경 지점에서 직접 처리한다.
    .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::none))
```

### 3-2. 명시적 회전 지점 네 곳

세션 ID 교체는 **상태가 실제로 바뀌는 네 지점**에서만 발생한다. 공통 로직은 `AuthSessionManager.bindPrincipal`(`AuthSessionManager.java:35-45`) — 기존 세션을 `invalidate()`하고 `request.getSession(true)`로 새 ID를 발급한다.

| # | 지점 | 성격 | 코드 위치 |
|:---:|:---|:---|:---|
| 1 | 로컬 로그인 성공 | 권한 상승 (게스트→회원) | `AuthLoginService.java:65` |
| 2 | OAuth2 로그인 성공 | 권한 상승 | `OAuth2LoginSuccessHandler.java:45` |
| 3 | 소셜 가입 완료 (약관 동의) | 상태 변경 (`termsAgreed=false→true`) | `SocialSignupController.java:34` |
| 4 | 판매자 등록 | 권한 상승 (`BUYER→SELLER`) | `SellerRegistrationController.java:34` |

이 네 지점에서만 회전하면, 공격자가 가로챈 세션 ID는 **다음 권한 상승·상태 변경 시점에 반드시 무효화**된다. 매 요청마다 회전하지 않더라도 공격 창은 "다음 상태 변경까지"로 자연스럽게 제한된다.

### 3-3. 회전이 실제로 어떻게 일어나는가 — 세션 조회·교체 메커니즘

"기존 세션을 찾아 invalidate하고 새 세션을 발급한다"가 실제로는 **쿠키 → Spring Session Filter → Redis** 세 단계로 처리된다. 애플리케이션 코드가 쿠키를 직접 파싱하거나 Redis를 직접 조회하지 않는다.

```
[재로그인 요청]
POST /api/auth/login
Cookie: SID=xyz789          ← ① 브라우저가 자동으로 실어서 보냄
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  ② SessionRepositoryFilter  (Spring Session이 자동 등록한 필터)  │
│                                                                 │
│    - Cookie 헤더에서 "SID"를 꺼냄 → xyz789                       │
│    - Redis HGETALL moongcheap:session:sessions:xyz789           │
│    - 발견되면 HttpSession 으로 wrap 후 request 에 주입            │
│    - 없으면 (쿠키 없음·만료·삭제) null 상태로 통과                │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
③ AuthLoginService.login → AuthSessionManager.bindPrincipal(...)
         │
         ▼
   HttpSession existing = request.getSession(false);
                           ↑
                    ②에서 wrap 해둔 세션을 그대로 반환
                    (추가 Redis 조회 없음 — 필터에서 1회만 조회)
         │
         ▼
   if (existing != null) existing.invalidate();  // Redis DEL moongcheap:session:sessions:xyz789
   var session = request.getSession(true);       // 새 세션 생성 → SID=qwe456
                                                 // 응답에 Set-Cookie: SID=qwe456
```

**세 가지 핵심 포인트**

1. **브라우저가 자동으로 쿠키를 포함** — Same-Origin 쿠키 규약에 따라 동일 도메인·경로 요청마다 `SID` 쿠키가 자동 전송된다. 쿠키 이름·속성은 `application.yml`에서 선언:

   | 속성 | 값 | 효과 |
   |:---|:---|:---|
   | `name` | `SID` | 쿠키 이름 |
   | `http-only` | `true` | JS 접근 차단 (XSS 방어) |
   | `secure` | `true` | HTTPS 외 전송 금지 |
   | `same-site` | `Lax` | 외부 사이트의 POST에선 전송 안 됨 (CSRF 방어) |

2. **`SessionRepositoryFilter`가 Redis 조회를 전담** — `@EnableRedisIndexedHttpSession(redisNamespace = "moongcheap:session")`이 이 필터를 자동 등록. **요청당 Redis 조회 1회**로 제한되어 `getSession(false)`를 여러 번 호출해도 추가 I/O가 없다.

3. **서블릿 표준 API로 추상화** — 애플리케이션 코드는 `request.getSession(false)` 하나만 호출한다. "쿠키에서 SID 파싱 → Redis에서 조회 → `HttpSession` 포장" 로직은 Spring Session 아래에 숨겨져 있어 **애플리케이션은 저장소를 몰라도 된다** (테스트에서 MockMvc 사용 가능, Redis를 다른 구현으로 바꿔도 코드 변경 없음).

### 3-4. "기존 세션을 못 찾는" 경우와 영향

`request.getSession(false)`가 `null`을 반환하면 `bindPrincipal`은 **기존 세션을 invalidate하지 않고** 새 세션만 발급한다. 발생 조건:

| 상황 | 원인 |
|:---|:---|
| 쿠키 자체가 없음 | 사용자가 쿠키를 지웠거나, 첫 방문 |
| SID는 있지만 Redis에 없음 | TTL 만료, 다른 경로의 `invalidate()`, Redis 재시작 |
| 네임스페이스 변경 | 배포 시 네임스페이스 변경 (일반적으로 발생 안 함) |

이 중 **"쿠키 지우고 재로그인"** 케이스에서 **좀비 세션**(= 사용자는 로그아웃했다고 생각하지만 Redis에는 세션이 남음)이 생길 수 있다. 다만:

- 세션 TTL(24h / 14d)로 자동 소멸되므로 **영구 누출은 아님**
- 보안이 민감한 전역 차단은 비밀번호 변경·탈퇴 경로에서 **memberId 인덱스**(`PRINCIPAL_NAME_INDEX_NAME`)로 모든 세션을 삭제하도록 분리 처리 (`invalidateAllForMember`, `invalidateAllExceptCurrent`)

트레이드오프 상세는 §5 참조.

---

## 4. 왜 매 HTTP 요청마다 회전하지 않았나

이론상 가장 안전한 선택은 **모든 요청마다 세션 ID 회전**이다. 실제로 Spring Security 기본값(`sessionFixation(migrateSession/newSession/changeSessionId)`)이 이 방향에 가깝다. 그러나 이 서비스에서는 다음 세 가지 이유로 "**권한·상태 변경 지점에서만**"으로 축소했다.

### 4-1. 동시 요청에서 RENAME 폭주 → 401 폭발 (실측)

50-스레드 동시성 테스트에서 로그인은 성공했는데 후속 요청들이 **401을 받는** 현상을 재현했다. Redis MONITOR로 추적한 결과:

```
RENAME session:A → session:B   OK
RENAME session:A → session:C   ERR no such key
RENAME session:A → session:D   ERR no such key
...  (총 50회 중 49회 실패)
```

- 동일한 세션 쿠키로 들어온 N개의 동시 요청이 각자 **다른 새 ID로 RENAME**을 시도
- 첫 요청만 성공하고 나머지 N-1개는 `no such key`로 **세션 자체가 날아감**
- 결과: 로그인은 성공했는데 뒤따르는 요청들이 세션을 못 찾아 401

이는 자동 회전이 "**멱등하지 않은 상태 변경**"이기 때문에 생기는 구조적 문제다. **매 요청 회전은 멀티 pod·동시 요청 환경에서 유지하기 어렵다**는 결론을 실측으로 얻었다.

> 상세 추적 기록: [`session-rotation-race-and-awarding-path.md`](../troubleshooting/session-rotation-race-and-awarding-path.md)

### 4-2. 비용

세션 ID 회전은 Redis `RENAME` + 쿠키 재발급(응답 헤더에 `Set-Cookie` 포함)을 수반한다.

- Redis RENAME은 요청 수에 비례해 선형 증가 — 트래픽이 많을수록 **인증 경로가 Redis 쓰기 비용을 떠안음**
- `Set-Cookie`가 매 응답에 실리면 CDN·캐시 레이어와의 상호작용이 복잡해짐 (캐시 분기 키·`Vary` 처리 등)

### 4-3. 보안 효과의 체감 차이가 작음

"**매 요청 회전**"과 "**권한·상태 변경 지점 회전**"의 보안 공백은 "**다음 상태 변경까지의 시간**"뿐이다. 공격자가 세션 ID를 가로채도:

- 다음 로그인·판매자 등록·소셜 가입 완료 중 아무거나 발생하면 세션 ID가 바뀜
- 비밀번호 변경·탈퇴 시에는 **다른 세션 자체가 삭제**되어 가로챈 세션이 통째로 무효

따라서 "매 요청 회전"으로 얻는 추가 보안 이득보다, 4-1·4-2의 운영 비용이 크다고 판단했다.

---

## 5. 트레이드오프와 알려진 한계

| 항목 | 내용 | 완화책 |
|:---|:---|:---|
| **Redis가 SPOF** | 세션·블랙리스트·큐가 Redis에 집중 | Sentinel/Cluster 구성, 세션 TTL로 접근 복제본 분산 |
| **Stateful 비용** | 세션 1건 ≈ 수백 바이트, 24h TTL 기준 10만 MAU에서 ~수십 MB | TTL + Remember-Me 분리로 활성 세션 수 제한 |
| **세션 ID 가로채기 창** | "다음 상태 변경까지" 열려 있음 | HttpOnly + Secure + SameSite=Lax, 비밀번호 변경 시 다른 기기 세션 삭제 |
| **좀비 세션** | 쿠키 삭제 후 재로그인 시 Redis에 orphan 세션 잔존 (§3-4) | 세션 TTL(24h / 14d)로 자동 소멸, 전역 차단은 `invalidateAllForMember` 경로 사용 |
| **SessionManagementFilter 자동 회전과의 충돌** | Spring Security가 기본값이면 매 요청 RENAME → 4-1의 폭주 재현 | `sessionFixation(none)` 명시 |

---

## 6. 재검토가 필요한 상황

- **외부 API 노출 비중이 커지는 경우** — 모바일 네이티브 외부 호출 비중이 커지면 쿠키 기반이 번거로워질 수 있음. 그 시점에 **API 토큰(JWT 또는 opaque token) 보조 레이어**를 세션과 병행 검토.
- **멀티 리전 배포** — Redis 지역 간 복제 지연이 세션 일관성을 깨는 지점에서는 세션 토큰 + 로컬 캐시 전략 재검토.
- **Session Fixation 자동 회전으로 복귀** — Spring Security가 미래에 "요청 ID별 멱등 회전"(예: `changeSessionId()`를 멱등하게 처리) 패치를 제공하면 재적용 검토.

---

## 참고 자료

- [인증·인가 정책 및 권한 매트릭스](auth-policy-and-permission-matrix.md) — 세션 정책(TTL/쿠키/무효화 시나리오) 스펙
- [세션 RENAME 폭주 트러블슈팅](../troubleshooting/session-rotation-race-and-awarding-path.md) — "매 요청 회전" 폐지의 실측 근거
- `src/main/java/com/moongcheap_backend/auth/infrastructure/SecurityConfig.java:50-53` — `sessionFixation(none)`
- `src/main/java/com/moongcheap_backend/auth/infrastructure/session/AuthSessionManager.java` — `bindPrincipal` · `invalidateAllForMember` · `invalidateAllExceptCurrent`
