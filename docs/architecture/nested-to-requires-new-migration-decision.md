# `Propagation.NESTED` → `REQUIRES_NEW` (outer 제거) 전환 결정

## 배경

### 추가적으로 nested 적용(1차 수정)한 내용 추가 -> 왜 하나의 tranasction으로 잡지 않고 이렇게 나누어 요청을 보내는 방식을 사용했는가

배치 오케스트레이션 메서드(`applyFormationPlan`, `applySubstituteOfferPlan`, `applyAwardingResult`)가
`@Transactional` outer + 각 iteration `Propagation.NESTED` inner 조합으로 구성되어 있음.

- 실패 격리 목적: 한 iteration 실패 시 그 iteration만 rollback, 다른 iteration은 계속 진행
- DB 관점에선 savepoint로 부분 rollback 가능
- 그러나 **JPA(Hibernate) 환경에서 NESTED는 사실상 안티패턴**

이 문서는 그 이유와 대체 방향의 결정 근거를 정리한다.

---

## 1. NESTED + JPA의 근본 문제

### 원인: Hibernate Session은 savepoint를 인지하지 못함

- DB는 savepoint를 인식하고 부분 rollback을 정확히 수행
- 그러나 Hibernate Persistence Context(1차 캐시)는 **savepoint 개념 없음**
- Session은 오직 자체 dirty tracking 상태로 관리

### 데이터 유실 시나리오 (silent data loss)

```
[Proposal 1 성공]
SAVEPOINT SP1
  demand1.substituteOffer(...)   ← Session dirty 마킹 (SQL 안 나감)
RELEASE SP1                       ← D1 UPDATE 여전히 미flush 상태

[Proposal 2 실패]
SAVEPOINT SP2
  existsBy... 쿼리 실행
    → auto-flush 트리거
    → D1의 UPDATE 실행 (SP2 범위 안에서 물리 발행)
    → Session: D1 clean 마킹, 스냅샷 갱신
  검증 실패 → throw BusinessException
ROLLBACK TO SP2
  → SP2 안의 변경 undo → D1 UPDATE도 함께 undo (SP2 범위 규칙)
  → 그러나 Session은 D1을 여전히 clean으로 인식

[outer commit]
Session flush 스캔 → D1은 clean → SQL 발행 안 함
COMMIT 정상 종료
→ D1의 변경이 조용히 사라짐
→ 응답 DTO: appliedCount 증가로 "성공" 카운트
→ 예외 없음, 로그 없음
```

**핵심 오해 정정**:

- SP rollback 범위는 정상 (SP 이후만 undo)
- 문제는 논리적으로 SP1에 속했어야 할 D1의 UPDATE가
  JPA lazy flush로 인해 SP2 안에서 실행됨
- SP2 rollback이 규칙대로 동작하면서 이 UPDATE까지 함께 undo

### 관찰 가능성

- 예외 없음
- 로그 없음
- 응답은 성공
- DB만 직접 확인해야 발견
- QA에서 특정 순서(성공-실패-성공)로 테스트해야 재현

---

## 2. 락(Lock) 관점의 추가 문제

### PostgreSQL 락 규칙

- 락은 **트랜잭션 종료 시점**까지 유지 (기본)
- `RELEASE SAVEPOINT`: 락 **유지** (savepoint 마커만 제거)
- `ROLLBACK TO SAVEPOINT`: 그 savepoint 안에서 획득한 락만 해제
- `COMMIT` / `ROLLBACK` (tx 종료): 모든 락 일괄 해제

### NESTED 시나리오의 락 누적

```
outer BEGIN
  iter 1: SELECT FOR UPDATE demand1  → row lock 획득
          (SP1 release 후에도 유지)
  iter 2: SELECT FOR UPDATE demand2  → row lock 획득
  ...
  iter 100: SELECT FOR UPDATE demand100
outer COMMIT ← 여기서 100개 락 일괄 해제
```

- 배치 진행 내내 **최대 100개 row가 동시 잠금**
- 다른 워커/사용자 요청은 blocking (수 초 대기 가능)
- 데드락 확률 증가 (락 조합 폭증)

### REQUIRED 대비

| 항목              | NESTED (현재)    | REQUIRED (outer 제거) |
|-----------------|----------------|---------------------|
| Peak 동시 락 개수    | 최대 100개        | 1개                  |
| demand1 락 유지 시간 | ~5~10초 (전체 요청) | ~50ms (iter만)       |
| 다른 워커 blocking  | 심함             | 미미                  |
| 데드락 위험          | 높음             | 낮음                  |

---

## 3. 검토한 옵션과 트레이드오프

### 옵션 A: NESTED 유지 + 명시적 flush

```java
public void substituteOffer(...) {
    ...
    demand.substituteOffer(demandBoardId);
    entityManager.flush();   // SP 안에서 강제 flush
}
```

**장점**: 커넥션 1개, 코드 변경 최소  
**단점**: 지속적으로 지켜야 할 숨은 규약 존재

- outer TX가 배치 호출 전에 dirty 엔티티 만들면 안 됨
- nested 메서드에서 mutation → flush 순서 유지
- bulk update와 엔티티 조작 섞지 말 것
- 실패 경로에서 managed entity detach 필요

규약 위반 시 silent data loss 재발. 팀 전체가 지속적으로 지키기 어려움.  
**락 누적 문제도 그대로 남음.**

### 옵션 B: REQUIRES_NEW (outer @Transactional 유지)

```java
@Transactional
public ...

applyBatch(...) {   // outer 유지
    for (...){
        self.inner(...);        // @Transactional(REQUIRES_NEW)
    }
}
```

**장점**: Session 자체 분리로 안전  
**단점**:

- outer suspended + inner 실행 → **peak 2 커넥션/요청**
- 동시 요청 많으면 커넥션 풀 고갈 위험
- outer가 하는 일 없는데 트랜잭션 열어둠 (낭비)

### 옵션 C: outer 제거 + REQUIRES_NEW (★ 채택)

```java
public ...

applyBatch(...) {    // @Transactional 제거
    for (...){
        self.inner(...);         // @Transactional(propagation = REQUIRES_NEW)
    }
}
```

**장점**:

- Session 자체 분리 → silent data loss 원천 차단
- 각 iteration이 완전 독립 tx
- Peak 커넥션 1개 (outer 제거로 suspend할 tx 없음)
- 락 누적 없음 (매 iter마다 해제)
- 코드가 명시적, 학습 곡선 낮음

**단점**:

- 100개 tx의 BEGIN/COMMIT 오버헤드 (~100~300ms)
- WAL fsync 100회 (필요 시 `SET LOCAL synchronous_commit = off`로 완화)

#### REQUIRED vs REQUIRES_NEW 선택 근거

지금 상태(outer 제거)에서 두 옵션의 **런타임 동작은 완전히 동일**:

- REQUIRED: outer 없음 → 새 tx 생성
- REQUIRES_NEW: 정지할 outer 없음 → 새 tx 생성

**REQUIRES_NEW를 선택한 이유는 미래 방어**:

- 나중에 누군가 outer 오케스트레이터에 `@Transactional`을 다시 붙였을 때
    - REQUIRED: outer에 편입 → 원래 NESTED 유사 문제(Session 공유·락 누적) 재발 위험
    - REQUIRES_NEW: 계속 격리 유지 → 안전
- 즉 REQUIRES_NEW는 "리팩터링 실수에 대한 보험"

성능·격리·자원 사용 모두 동일하고 방어력만 우위이므로 REQUIRES_NEW 채택.

### 옵션 D: 벌크 SQL

**적합하지 않음**:

- per-item 성공/실패 분류 요구사항과 충돌
- 도메인 검증 로직이 SQL에 파묻힘
- 유지보수 비용 폭증
- 지금 규모(100 proposals, 배치 API)엔 오버킬

---

## 4. PostgreSQL 엔진 관점의 트레이드오프

REQUIRES_NEW (옵션 C)로 전환 시 100 tx/요청 발생. 각 관점별 영향:

| 측면        | 영향                    | 대응                               |
|-----------|-----------------------|----------------------------------|
| WAL fsync | 100회로 증폭              | `synchronous_commit = off` 세션 옵션 |
| XID 소비    | 100배 가속               | 대규모 트래픽 아니면 무관, autovacuum 튜닝    |
| MVCC 스냅샷  | short tx는 오히려 유리      | 이득                               |
| 락 홀드 시간   | 대폭 단축                 | 이득 (경합·데드락 감소)                   |
| 백엔드 프로세스  | 변화 없음 (같은 커넥션 재사용)    | 이슈 없음                            |
| 복제 오버헤드   | 스트리밍은 미미, logical은 중간 | 상황에 따라 판단                        |
| 프로토콜 왕복   | 200회 추가               | 로컬 DB면 ~20ms, 원격은 ~200ms         |

**결론**: 배치 API 성격이고 초당 요청 수가 많지 않은 지금 규모에선
모든 관점에서 감당 가능하며, 락·격리 이득이 훨씬 큼.

---

## 5. 결정

### 변경 대상

- `applyFormationPlan` / `applyExistingAssignment`, `createNewBoard`
- `applySubstituteOfferPlan` / `substituteOffer`
- `applyAwardingResult` / `award`

### 변경 내용

**Before**:

```java
@Transactional
public ...

applyXxxPlan(...) {
    for (...){
        self.xxx(...);
    }
}

@Transactional(propagation = Propagation.NESTED)
public void xxx(...) { ...}
```

**After**:

```java
public ...

applyXxxPlan(...) {   // @Transactional 제거
    for (...){
        try {
            self.xxx(...);
        } catch (BusinessException e) { ...} catch (DataAccessException e) { ...}
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void xxx(...) { ...}
```

### 결정 근거 요약

1. **Silent data loss 방지**: Session 자체가 분리되어 auto-flush + savepoint rollback 조합 함정 원천 차단
2. **락 격리**: 매 iteration마다 락 해제 → 다른 워커·요청 blocking 최소화
3. **커넥션 풀 부담 없음**: outer 제거로 정지할 tx 없어 peak 1개만 사용
4. **팀 러닝코스트 최소**: Spring 표준 패턴, 숨은 규약 없음
5. **미래 방어**: outer가 다시 추가돼도 REQUIRES_NEW는 계속 격리 유지
6. **성능 비용 감내 가능**: 100~300ms 오버헤드는 배치 API 성격상 무의미

### 채택하지 않은 옵션의 사유

- **NESTED 유지 + flush**: 숨은 규약 유지 부담, silent bug 재발 위험, 락 누적 문제 미해결
- **REQUIRES_NEW + outer 유지**: 커넥션 2배 사용, 풀 압박 위험
- **outer 제거 + REQUIRED**: 지금 동작은 REQUIRES_NEW와 동일하나, 미래에 outer 재도입 시 격리가 깨질 여지 있음 (방어력 열세)
- **벌크 SQL**: per-item 오류 분류 모델과 충돌, 유지보수 비용 폭증

---

## 6. 후속 작업

### 즉시 반영 (완료)

- 세 오케스트레이터의 `@Transactional` 제거 (`applyFormationPlan`, `applySubstituteOfferPlan`,
  `applyAwardingResult`)
- inner 메서드들의 propagation을 `REQUIRES_NEW`로 지정
    - `applyExistingAssignment`, `createNewBoard`, `substituteOffer`, `award`
- 컴파일 확인, 단위 테스트 실행

### 정리 여지 (선택)

- `createNewBoard`의 `entityManager.detach(saved)` — NESTED 시절 방어 코드였음. REQUIRES_NEW에선 각 tx의
  persistence context가 자동 폐기되므로 불필요. 제거해도 무방.

### 검증

- 통합 테스트: `[성공, 실패, 성공]` 순서로 배치 실행 후 DB 상태 확인
    - Before: 유실 가능 (silent data loss)
    - After: 성공한 것들만 정확히 반영, 실패한 것은 skip
- 락 모니터링: `pg_locks` 관찰하여 락 누적 없는지 확인

### 튜닝 여지 (필요 시)

- fsync 부담 크면 세션 단위 `SET LOCAL synchronous_commit = off`
- 트래픽 급증 시 autovacuum 파라미터 조정
- 초당 요청 수 증가 시 벌크 SQL 재검토

---

## 7. 참고 자료

- [nested-transaction-substitute-offer-analysis.md](../troubleshooting/nested-transaction-substitute-offer-analysis.md) —
  원본 문제 분석
- Spring Framework Reference — Transaction Propagation
- PostgreSQL Documentation — Savepoints, MVCC, WAL
- Hibernate ORM Documentation — Persistence Context, Flush Modes
