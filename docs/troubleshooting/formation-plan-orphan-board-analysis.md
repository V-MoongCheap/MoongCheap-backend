# Formation Plan: Orphan Board 리스크 분석

`DemandBoardService.applyFormationPlan`에서 신규 board를 만들 때 발생할 수 있는 orphan(고아) board 리스크를 검토하고, 방어 코드를 **적용하지 않은 이유**를 정리한다.

관련 코드: `DemandBoardService.createNewBoards`, `DemandRepository.assignToBoard`

---

## 문제 제기

`createNewBoards`의 흐름:

```
1. newBoards → DemandBoard 엔티티로 변환
2. demandBoardRepository.saveAll(...)  ← INSERT 발생
3. 각 board별로 demandRepository.assignToBoard(...)
4. assignToBoard가 updated < demandIds.size 를 반환하면 → BusinessException
```

**우려 시나리오**

`assignToBoard`는 `WHERE status = 'UNASSIGNED'` 필터를 가진다. 만약 신규 board에 배정하려는 demand가 이미 다른 곳에서 `ASSIGNED` 상태로 바뀌었다면:
- 3단계에서 `updated < demandIds.size` → 예외 발생
- 하지만 **board는 이미 2단계에서 INSERT됨**
- 트랜잭션이 롤백되지 않는다면 → orphan board가 DB에 남음

## 현재 코드는 안전한가

**안전합니다.** 이유:

1. `applyFormationPlan` 전체가 `TransactionTemplate.executeWithoutResult`로 감싸져 있음
2. `BusinessException`은 `RuntimeException` → 트랜잭션 롤백 트리거
3. 롤백 시 `saveAll`의 INSERT도 함께 롤백됨
4. DB에는 아무것도 남지 않음

즉, **현재 구조에서 orphan은 발생 불가**.

## 그럼에도 방어 코드를 검토한 이유

향후 리팩토링 실수로 다음 상황이 벌어질 수 있음:

- 트랜잭션 경계가 잘못 축소되어 `saveAll`이 커밋 후 assign이 별도 트랜잭션에서 실행
- `BusinessException`을 상위에서 catch만 하고 rethrow하지 않음
- `saveAll`과 `assignToBoard` 순서가 바뀜

이 경우 orphan board가 실제로 생김.

## 검토한 방어 방법: 사전 검증

```java
private void createNewBoards(List<NewBoard> newBoards, LocalDateTime now) {
    if (newBoards == null || newBoards.isEmpty()) return;

    // saveAll 전에 모든 demand가 UNASSIGNED인지 검증
    List<Long> allDemandIds = newBoards.stream()
        .flatMap(nb -> nb.demandIds().stream())
        .toList();
    long unassignedCount = demandRepository.countByIdInAndStatus(
        allDemandIds, DemandStatus.UNASSIGNED);
    if (unassignedCount != allDemandIds.size()) {
        throw new BusinessException(ErrorCode.DEMAND_ASSIGNMENT_MISMATCH);
    }

    // 이후 saveAll + assignToBoard
    ...
}
```

## 적용하지 않은 이유

### 1. 현재 리스크가 0

트랜잭션이 정상 동작하는 한 orphan은 불가능. **존재하지 않는 문제를 위한 방어 코드**를 추가하는 셈.

### 2. Race condition 100% 방어 아님

검증 시점과 `saveAll` 사이에도 상태가 바뀔 수 있음. 결국 최종 방어는 `assignToBoard`의 row count 체크 + 트랜잭션 롤백에 의존해야 함. 사전 검증만으로 완전 방어는 불가.

### 3. 정상 트래픽에 오버헤드 부과

- 성공 케이스(대부분)에서 `count` 쿼리 1회 추가
- 실패 케이스는 드물기 때문에, 실패 방어를 위해 성공 케이스에 세금을 매기는 꼴

### 4. YAGNI 원칙

"미래에 누가 실수할지 모르니" 방어 코드는 **가정에 근거한 방어**. 실제 리팩토링 시 코드 리뷰·테스트로 잡히는 게 더 확실함.

### 5. 얻는 이점의 성격

방어 코드가 유일하게 실질적으로 얻는 것:
- 실패 시 **DB에 무의미한 INSERT/롤백 트래픽 안 감** (WAL 감소, sequence 소비 안 됨)

하지만 이는 실패 케이스에 국한. 배치성 formation plan은 실패가 매우 드문 상황이므로 이 이득도 미미.

---

## 트레이드오프 표

| 항목 | 현재 (검증 없음) | 사전 검증 추가 |
|------|------------------|----------------|
| 성공 케이스 쿼리 수 | assignToBoard N개 | **count 1개 + assignToBoard N개** |
| 현재 구조의 orphan 리스크 | 없음 (롤백 보장) | 없음 |
| 트랜잭션 미사용 실수 시 orphan | 있음 | 있음 (race condition 존재) |
| 실패 시 실제 실행된 쿼리 | INSERT + UPDATE (롤백) | count만 |
| WAL/sequence 소비 (실패 시) | 있음 | 없음 |
| 코드 복잡도 | 단순 | 검증 로직 추가 |

## 대신 지켜야 할 원칙

방어 코드 대신 아래 원칙을 코드 리뷰·테스트로 강제:

1. `applyFormationPlan`은 반드시 트랜잭션 안에서 실행 — 현재 `TransactionTemplate`로 보장
2. `BusinessException`은 서비스에서 catch하지 말고 컨트롤러/전역 예외 핸들러에서만 처리
3. `saveAll`과 `assignToBoard`의 순서를 바꾸지 말 것 (주석으로 명시 고려)
4. FK 제약 (`demand.demand_board_id → demand_board.id`)이 DB 레벨에서 유지되는지 확인

## 재검토 트리거

다음 상황이 오면 이 결정을 재검토:

- Formation plan이 매우 빈번해지고 실패율이 유의미해질 때 (WAL 부하가 실측될 때)
- Sequence 소비량이 문제가 될 정도로 커질 때
- 트랜잭션 경계를 세분화해야 하는 요구사항이 생길 때 (예: 부분 성공 허용)

---

## 관련 문서

- [`transaction-timeout.md`](../operations/transaction-timeout.md) — 트랜잭션·락 타임아웃 정책
- [`retry-and-logging.md`](../operations/retry-and-logging.md) — Retry 및 로깅 정책
- [`ai-awarding-flow.md`](../domain/ai-awarding-flow.md) — AI 낙찰 플로우
