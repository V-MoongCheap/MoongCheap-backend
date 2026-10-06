# `Propagation.NESTED` + 배치 처리 패턴의 위험성 분석

## 배경

`applySubstituteOfferPlan` 은 여러 개의 `Proposal` 을 순회하며 하나씩 처리하고, 각 proposal 의 실패는 격리하여 배치 전체가 중단되지 않도록 설계된 배치 오케스트레이션 메서드다.

각 proposal 을 처리하는 `substituteOffer` 는 `Propagation.NESTED` 로 선언되어 있고, `JpaTransactionManager` 는 `nestedTransactionAllowed=true` 로 설정되어 있다.

```java
@Bean
public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    JpaTransactionManager tm = new JpaTransactionManager(emf);
    tm.setNestedTransactionAllowed(true);
    return tm;
}
```

## 대상 코드

```java
@Transactional
public SubstituteOfferPlanResponseDto applySubstituteOfferPlan(
    SubstituteOfferPlanRequestDto request) {
    int appliedCount = 0;
    int staleRejectedCount = 0;
    int alreadyAppliedCount = 0;
    for (Proposal proposal : nullSafe(request.proposals())) {
        try {
            self.substituteOffer(
                proposal.demandId(),
                proposal.demandBoardId(),
                proposal.expectedOriginalCatalogId(),
                proposal.substituteCatalogId());
            appliedCount += 1;
        } catch (BusinessException e) {
            switch (e.getErrorCode()) {
                case DEMAND_SUBSTITUTE_ALREADY_APPLIED -> {
                    alreadyAppliedCount += 1;
                    log.info(...);
                }
                case DEMAND_BOARD_NOT_FOUND, DEMAND_NOT_FOUND,
                     DEMAND_SUBSTITUTE_NOT_ELIGIBLE -> {
                    staleRejectedCount += 1;
                    log.warn(...);
                }
                default -> throw e;
            }
        }
    }
    return new SubstituteOfferPlanResponseDto(
        SubstituteOfferPlanResponseDto.Status.APPLIED,
        appliedCount, alreadyAppliedCount, staleRejectedCount);
}

@Transactional(propagation = Propagation.NESTED)
public void substituteOffer(
    Long demandId, Long demandBoardId,
    Long expectedOriginalCatalogId, Long substituteCatalogId) {
    if (!demandBoardRepository.existsByIdAndStatusAndCatalogId(...)) {
        throw new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND);
    }
    Demand demand = demandRepository.findByIdAndSubstitutableForUpdate(demandId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_NOT_FOUND));
    // ... 검증 ...
    demand.substituteOffer(demandBoardId);   // dirty checking, 명시적 flush 없음
}
```

## 각 트랜잭션 전파 옵션 비교

| 전파 | 부분 실패 격리 | JPA 안전성 | 연결 사용 |
|---|---|---|---|
| `REQUIRED` (default) | 실패하면 rollback-only 마킹 → outer commit 시 `UnexpectedRollbackException` | | 1 |
| `NESTED` | DB는 savepoint 롤백으로 격리 | **Session 오염** — 후술 | 1 |
| `REQUIRES_NEW` | 완전 격리 | Session 도 분리됨 | 2 |
| outer 제거 + inner `REQUIRED` | 완전 격리 | Session 도 분리됨 | 1 (순차) |

## `NESTED` 의 근본적 문제

### 원리

`Propagation.NESTED` 는 outer 트랜잭션 안에서 **JDBC savepoint** 를 생성한다.

- inner 실패 → savepoint 까지만 rollback → outer 는 살아있음
- 연결(Connection) 은 1 개만 사용

DB 관점에서만 보면 배치의 부분 실패 격리에 적합해 보이지만, **Hibernate 의 1차 캐시(Persistence Context)는 savepoint 롤백을 인지하지 못한다**.

### 데이터 유실 시나리오 (재현 가능)

`substituteOffer` 는 `demand.substituteOffer(boardId)` 로 엔티티를 dirty 상태로 만들고 **명시적 flush 없이 리턴**한다. JPQL 쿼리(`existsBy...`, `findBy...`)는 `FlushMode.AUTO` 기본 동작으로 실행 전 auto-flush 를 트리거한다.

```
proposal 1 (성공):
  SP1 생성
  existsBy...                          (dirty 없음)
  SELECT FOR UPDATE demand1
  demand1.substituteOffer(boardId)     ← Session dirty
  return                               ← SP1 release, flush 안 일어남
                                       ← demand1 은 여전히 dirty (미flush)

proposal 2 (실패):
  SP2 생성
  existsBy...                          ← auto-flush 발동!
                                       → UPDATE demand1 실행 (SP2 안에서)
                                       → Session 이 demand1 을 clean 으로 마킹
                                       → 스냅샷 갱신
  SELECT FOR UPDATE demand2
  검증 실패 → BusinessException
  outer catch → rollback to SP2        ← DB 의 demand1 UPDATE 롤백!
                                       ← Session 의 demand1 은 여전히 clean

proposal 3, ... (성공/실패 무관):
  auto-flush 시 demand1 은 clean → 재flush 안 됨

최종 outer commit:
  Session flush → demand1 은 clean → SQL 안 나감
  → demand1 의 substituteOffer 가 조용히 사라짐
```

**증상**:
- 응답 DTO 의 `appliedCount` 는 증가
- DB 에는 변경 반영 안 됨
- 예외 없음, 로그 없음

### 재현 테스트 시나리오

```
proposals = [정상 A, stale B, 정상 C]
→ 응답: appliedCount=2, staleRejectedCount=1
→ DB 확인: A 의 status 가 SUBSTITUTE_OFFERED 로 바뀌었는가?
                                     (아마 아닐 것)
```

## `entityManager.detach()` 로 방어가 가능한가?

가능한 케이스: `createNewBoard` 처럼 "이 메서드에서 새로 만든 엔티티" 를 실패 경로에서 detach 하는 경우.

```java
@Transactional(propagation = Propagation.NESTED)
public Long createNewBoard(FormationPlanRequestDto.NewBoard newBoard, LocalDateTime now) {
    DemandBoard saved = demandBoardRepository.save(newBoard.toEntity());
    int updated = demandRepository.assignToBoard(...);
    if (updated != newBoard.demandIds().size()) {
        entityManager.detach(saved);
        throw new StaleFormationItemException();
    }
    return saved.getId();
}
```

**detach 가 해결하는 것**: `saved` 가 dirty 상태로 Session 에 남아 다음 auto-flush 때 재삽입되는 문제.

**detach 가 해결하지 못하는 것**:
1. Bulk update (`assignToBoard`) 실행 직전 auto-flush 로 **outer TX 가 이전에 만든 다른 dirty 엔티티** 가 savepoint 안에서 flush → 롤백으로 조용히 유실
2. Bulk update 로 인한 1차 캐시 stale (성공 시)
3. `substituteOffer` 처럼 **이전 성공 proposal 이 남긴 dirty** 는 이 메서드의 관심 밖 → detach 대상으로 인식되지 않음

즉 detach 는 지역적 방어일 뿐 Session 전체 일관성 문제를 해결하지 못한다.

## 해결책 (권장 순서)

### 1순위: `Propagation.REQUIRES_NEW`

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void substituteOffer(...) { ... }
```

- 각 proposal 이 별도 Connection / Session / TX 를 가짐
- Session 오염 원천 차단
- 커넥션 2 개 필요 (outer 1 + inner 1) — 순차 호출이므로 동시 2 개만 잡음
- 배치 크기가 커넥션 풀 크기를 넘지 않으면 실용적 문제 없음

### 2순위: outer `@Transactional` 제거 + inner `REQUIRED`

```java
// outer: TX 없음 (오케스트레이션만)
public SubstituteOfferPlanResponseDto applySubstituteOfferPlan(...) { ... }

// inner: 기본 REQUIRED
@Transactional
public void substituteOffer(...) { ... }
```

- 각 호출마다 새 TX / Session 이 열리고 닫힘
- 커넥션 1 개만 순차 사용
- 격리 완벽

### 3순위: NESTED 유지 + inner 종료 시 명시적 flush

정말로 NESTED 를 유지해야 한다면:

```java
demand.substituteOffer(demandBoardId);
entityManager.flush();  // SP 안에서 flush → 실패 시 SP rollback 으로 안전
```

**전제 조건**:
- outer TX 는 이 배치 호출 전까지 어떤 엔티티도 dirty 로 만들지 말 것
- NESTED 메서드 안에서 bulk update 와 엔티티 조작을 섞지 말 것
- 실패 경로에서 이 메서드가 만진 모든 managed entity 를 detach

이 규약을 팀 전체가 지속적으로 지킬 수 있는 게 아니라면 권장하지 않는다.

## 추가로 개선 여지가 있는 부분

`default -> throw e;` 분기에서 예상 못한 `BusinessException` 이나 다른 `RuntimeException`(예: `OptimisticLockException`, `DataIntegrityViolationException`) 은 그대로 전파되어 배치 전체가 중단된다.

- 앞서 성공한 `appliedCount` 가 응답으로 나가지 못함
- 재시도 / 스킵 정책 필요 여부 검토

관측성 관점에서 `staleRejectedCount` 는 여러 사유(`DEMAND_BOARD_NOT_FOUND` vs `DEMAND_NOT_FOUND` vs `NOT_ELIGIBLE`) 를 하나로 뭉갠다. 대량 처리 시 원인 분석이 어려워질 수 있으니 사유별 카운트를 분리하거나 로그 기반 집계를 강화하는 것을 고려한다.

## 결론

- `NESTED` 는 순수 JDBC/MyBatis 환경에서는 유용하지만, **JPA 환경에서는 사실상 안티 패턴**이다
- 현재 코드는 `NESTED` + `dirty entity 를 명시적 flush 없이 리턴` + `JPQL auto-flush` 조합으로 인해 확률적으로 데이터가 유실될 수 있다
- 가장 저렴하고 안전한 수정은 `Propagation.REQUIRES_NEW` 로 변경하는 것이다
