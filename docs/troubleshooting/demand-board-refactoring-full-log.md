# DemandBoardService 리팩토링 종합 기록

이 문서는 `DemandBoardService` 및 관련 코드에 대해 진행한 모든 리팩토링과 논의를 정리합니다.
결정 과정, 선택하지 않은 대안, 그리고 다음 단계로 남은 이슈를 모두 포함합니다.

## 목차

1. [CodeRabbit 지적 사항 대응](#1-coderabbit-지적-사항-대응)
2. [Chunk 기반 트랜잭션 리팩토링](#2-chunk-기반-트랜잭션-리팩토링)
3. [락 순서 정렬 (데드락 방어)](#3-락-순서-정렬-데드락-방어)
4. [IDENTITY → SEQUENCE 부분 전환](#4-identity--sequence-부분-전환)
5. [JDBC batchUpdate 도입](#5-jdbc-batchupdate-도입)
6. [검토했지만 적용하지 않은 것](#6-검토했지만-적용하지-않은-것)
7. [남아있는 이슈 및 다음 단계](#7-남아있는-이슈-및-다음-단계)

---

## 1. CodeRabbit 지적 사항 대응

### 1-1. `applyFormationPlan` 의 new-board 루프 예외 처리 부족

**문제:**
- 기존 루프는 `StaleFormationItemException` 만 catch
- `DataAccessException` 이나 `RuntimeException` 이 발생하면 루프 중단
- 이전 iteration 이 이미 `REQUIRES_NEW` 로 커밋됐지만 caller 는 non-200 응답 받음
- 클라이언트가 각 `clientBoardKey` 의 결과를 알지 못함 → **재시도 시 중복 board 생성 위험**

**적용:**
- `catch (DataAccessException e)` 추가 (data 접근 실패)
- `catch (RuntimeException e)` 추가 (예상치 못한 실패)
- 세 경우 모두 `NewBoardStatus.STALE_REJECTED` 결과 반환

**대안 검토 - CodeRabbit 이 제안한 새 `FAILED` 상태:**

CodeRabbit 은 별도의 `FAILED` 상태를 추가해서 stale 과 실패를 구분하라고 제안. 하지만:
- **이 프로젝트의 기존 스타일**: `applyExistingAssignment` 의 catch 도 세 종류 예외를 모두 `staleCount` 에 합침
- 신규 상태 추가 시 상반된 스타일로 혼란 유발
- 일관성 우선 → **CodeRabbit 제안 대신 STALE_REJECTED 로 통일**

이 판단은 CodeRabbit 의 "definitely correct" 판단과 다르지만, 코드베이스 컨벤션과의 일관성이 우선.

---

### 1-2. `applyAwardingResult` default 브랜치 카운트 누락

**문제:**
```java
default -> {
    log.error("Awarding failed with unexpected BusinessException: ...");
    // ← staleRejectedCount 증가 없음
}
```

- 예상치 못한 BusinessException (예: `GroupBuyService.createGroupBuy` → `PRODUCT_NOT_ORDERABLE`) 발생 시
- `award()` REQUIRES_NEW 트랜잭션 롤백 → board 는 `GB_AWARDING` 유지 → pending-board 쿼리에서 다시 잡힘
- 응답은 `APPLIED` 이지만 `appliedCount + staleRejectedCount` 가 요청 항목 수와 불일치
- Caller 가 "완료됐다" 로 오해할 수 있음

**적용:**
- `default` 브랜치에 `staleRejectedCount += 1` 추가
- 이후 chunk 리팩토링 시점에 자연스럽게 chunk-level 처리로 재구조화

---

### 1-3. `getPendingAwarding` 페이지네이션 불안정

**문제:**
- Offset 기반 pagination 사용
- `applyAwardingResult` 가 처리한 board 는 `GB_AWARDING` 에서 빠짐
- Page 0 처리 후 page 1 요청 시 offset 이 밀려 **row skip 발생**

**적용:**
- Offset 제거 → **queue-pop 패턴**으로 변경
- `LIMIT :size` 만 사용, 항상 GB_AWARDING 첫 N 개 반환
- 처리된 board 는 filter 에서 자연스럽게 빠지므로 다음 fetch 에 다음 대상이 잡힘
- 실패한 board 는 그대로 남아 자동 재시도

**API 변경:**
- `@PageableDefault Pageable pageable` → `@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size`
- 응답 DTO 에서 `page` 필드 제거, `hasNext` 유지 (`size + 1` fetch 로 판단)

**다른 pagination 은 왜 안 건드렸나:**
- `getHostDemandBoard`, `getByCatalogId`, `list` 등도 offset 기반이지만
- 이들은 **공개 브라우징 UI 용** (짧은 lifecycle, 배치 소비 패턴 아님)
- Write 가 filter 상태를 shift 시키지 않거나 무시 가능 수준
- `getPendingAwarding` 만 "AI 배치가 pull → 처리 → 다시 pull" 하는 소비 패턴이라 문제

---

### 1-4. `applyExistingAssignment` 자체 문제

CodeRabbit 지적 외에 논의된 것:
- 자체 REQUIRES_NEW → 각 assignment 마다 fsync 1회
- Chunk 로 묶으면 fsync 감소

이는 아래 [Chunk 리팩토링](#2-chunk-기반-트랜잭션-리팩토링) 에서 처리.

---

## 2. Chunk 기반 트랜잭션 리팩토링

### 2-1. 배경

기존 구조: 각 항목마다 `REQUIRES_NEW` 트랜잭션
- 항목 100개 = 트랜잭션 100개 = COMMIT 100회 = fsync 100회
- 각 fsync 는 1~10ms → 총 100~1000ms 낭비

### 2-2. 적용: Chunk 트랜잭션 (사이즈 10)

**대상 4개 API:**
- `applyExistingAssignmentChunk`
- `createNewBoardChunk`
- `substituteOfferChunk`
- `awardChunk`

각 chunk = 1 REQUIRES_NEW 트랜잭션 = 1 COMMIT = 1 fsync

**Chunk 크기 10 을 선택한 이유:**
- 여러 테이블 잡는 트랜잭션이라 락 홀드 시간 최소화 필요
- 20~50 은 데드락 위험 비선형 증가
- 100개 전체 처리 시 fsync 100 → 10 회로 감소 (10배)
- 실패 시 재시도 낭비량 chunk 사이즈에 비례 → 10 이면 감내 가능

### 2-3. Failure 처리 방침

**All-or-nothing (existing / new board):**
- chunk 안에서 하나 실패하면 전체 롤백
- 실패 카운트: `staleCount += chunkSize` (그룹핑된 카운팅)
- 재시도 시 stale 원인이 해소되면 정상 처리

**세분화 (substitute / award):**
- 상태 변경 이전에 던져지는 BusinessException 은 chunk 내부에서 catch 후 continue
- 다른 proposal / boardResult 는 정상 처리 유지
- `SubstituteOfferChunkResult(applied, alreadyApplied, staleRejected)` 반환
- `AwardingChunkResult(applied, staleRejected)` 반환

**왜 substitute/award 만 세분화?**
- Substitute offer 와 award 는 각 항목이 **완전히 독립적** (각각 다른 demand / board 를 처리)
- 하나 실패해도 나머지가 성공하는 것이 자연스럽고 이득도 큼
- Existing/new board 는 **AI plan 전체가 일관성**을 가져야 해서 all-or-nothing 이 안전

### 2-4. Self-invocation 이슈 발견 및 수정

**발견:**
- 리팩토링 중 `newBoardResults.addAll(this.createNewBoardChunk(...))` 발견
- `this.` 로 호출하면 Spring 프록시 우회 → `@Transactional(REQUIRES_NEW)` 무시
- 트랜잭션 없이 실행되면 각 SQL 이 auto-commit → **chunk 원자성 붕괴**

**수정:**
- `this.createNewBoardChunk(...)` → `self.createNewBoardChunk(...)`
- 프로젝트 전반의 다른 `self.` 호출들도 확인 완료

**규칙:**
- 같은 클래스 안의 `@Transactional` 메서드는 반드시 `self.` 통해 호출
- `this.` 또는 그냥 메서드명은 프록시를 거치지 않아 트랜잭션 안 걸림

---

## 3. 락 순서 정렬 (데드락 방어)

### 3-1. 배경

여러 concurrent chunk 가 같은 row 를 잡을 때 락 획득 순서가 다르면 데드락.
- 스레드 A: board 10 → board 20 → board 30
- 스레드 B: board 30 → board 20 → board 10
- **Circular wait → 데드락**

방어책: **모든 트랜잭션이 동일한 순서로 락 획득**.

### 3-2. 초기 구현 (chunk 내부 sort)

`applyExistingAssignmentChunk` 안에서 `demandBoardId` 오름차순 정렬:
```java
List<ExistingBoardAssignment> ordered = assignments.stream()
    .sorted(Comparator.comparing(ExistingBoardAssignment::demandBoardId))
    .toList();
```

### 3-3. 개선: `chunk()` 헬퍼에 Comparator 오버로드

**개선 이유:**
- 정렬 로직이 chunk 마다 중복
- `substituteOfferChunk`, `createNewBoardChunk` 에도 필요한데 누락돼 있었음
- Caller 에서 한 번 sort 하면 O(n log n), chunk 내부에서 하면 O(n log k) 로 이론상 후자가 더 빠르지만 실무적 차이 없음
- **API 시그니처로 sort 계약을 강제**하는 게 더 견고

**구현:**
```java
private static <T> List<List<T>> chunk(List<T> list, int chunkSize) {
    return chunk(list, chunkSize, null);
}

private static <T> List<List<T>> chunk(
    List<T> list, int chunkSize, Comparator<T> comparator) {
    List<T> safe = nullSafe(list);
    if (comparator != null) {
        safe = safe.stream().sorted(comparator).toList();
    }
    return IntStream.range(0, (safe.size() + chunkSize - 1) / chunkSize)
        .mapToObj(i -> safe.subList(...))
        .toList();
}
```

**Caller 별 정렬 키:**
- `ExistingBoardAssignment` → `demandBoardId`
- `NewBoard` → `Collections.min(demandIds)` (리스트라 min 값 사용)
- `Proposal` → `demandId`
- `BoardResult` → `boardId`

### 3-4. 락 관점에서 내부 sort vs 외부 sort 는 동일

**논의:** 성능 관점에선 미묘한 차이 있지만 락 관점에선 완전히 동등.
- 어느 쪽이든 각 트랜잭션 안에서 오름차순 락 획득
- 데드락 방지 조건 동일
- 코드 명확성으로 외부 sort 선택

---

## 4. IDENTITY → SEQUENCE 부분 전환

### 4-1. 배경

**Hibernate 는 IDENTITY 엔티티의 `save()` 를 즉시 실행**:
- `EntityIdentityInsertAction` 이 즉시 실행 액션으로 등록
- ID 를 DB 로부터 받아야 하므로 INSERT 를 batch 로 묶을 수 없음
- 결과: N 개 INSERT = N 개 RTT

**SEQUENCE 전환 시:**
- Sequence 에서 미리 ID 발급 (cache 활용 시 즉시)
- INSERT 는 flush 시점까지 지연 가능
- `hibernate.jdbc.batch_size` 크기만큼 batch 로 묶어 전송
- N 개 INSERT ≈ 1~2 RTT

### 4-2. ✅ ProductAwardEvaluation — 전환 완료

**이유:**
1. **Bulk INSERT 규모 큼**: award chunk 당 board × evaluations = 20~100 INSERT
2. **FK 종속 없음**: 저장 후 다른 native query 가 이 테이블 새 row 참조 안 함
3. **ID 사용 안 함**: `saveAll(evaluations)` 후 결과 ID 를 쓰지 않음 → batch 완전 활용
4. **코드 변경 없음**: 어노테이션과 스키마만 변경
5. **마이그레이션 리스크 낮음**: 참조 관계 단순

**적용 내용:**
- `V13__product_award_evaluation_sequence.sql`: sequence 생성, 컬럼 IDENTITY 제거, MAX(id) 로 초기화
- `ProductAwardEvaluation.java`: `@SequenceGenerator(allocationSize = 20)`
- `application.yml` 은 이미 `jdbc.batch_size: 20`, `order_inserts: true` 있어서 추가 설정 불필요

**예상 이득**: chunk 당 ~48 RTT 절약

### 4-3. ❌ DemandBoard — 전환 안 함

**결정 이유:**
1. **FK 종속으로 batching 이득 감소**: `createNewBoardChunk` 안에서 save 직후 `assignToBoard` 가 새 board id 를 FK 로 참조 → 명시적 flush 필요 → "지연" 이득 상실
2. **Batching 이득 상대적으로 작음**: chunk 10 board 기준 ~8 RTT 절약 (ProductAwardEvaluation 의 ~48 RTT 대비 작음)
3. **코드 복잡도 상승**: `entityManager.flush()` 명시 추가 필요
4. **트래픽 특성**: Formation-plan 은 award 보다 자주 안 돌아감
5. **참조 관계 넓음**: `demand`, `product`, `product_award_evaluation`, `reject_history` 등 여러 테이블에서 참조 → 신중한 마이그레이션 필요

### 4-4. 나머지 18개 IDENTITY 엔티티 — 유지

**이유**: 대부분 단건 INSERT 패턴 (회원가입, 결제 등). Batch 자체가 무의미.

**참고**: `Orders` 는 이미 SEQUENCE — `OrderService` 의 batch order 생성에서 이득을 얻고 있음.

**미래 후보**: `OutboxEvent`, `Notification` — 향후 이벤트/알림 대량 발행 기능 확장 시 전환 검토.

---

## 5. JDBC batchUpdate 도입

### 5-1. 배경

Chunk 트랜잭션으로 fsync 는 감소했지만, **chunk 안의 UPDATE 는 여전히 순차 RTT**:
- `applyExistingAssignmentChunk`: for 루프로 `assignToExistingBoard` 10회 호출 = 10 UPDATE RTT
- `createNewBoardChunk`: for 루프로 `assignToBoard` 10회 호출 = 10 UPDATE RTT

### 5-2. 적용: `DemandBatchRepository`

`NamedParameterJdbcTemplate.batchUpdate()` 기반 신규 repository:
- `batchAssignToExistingBoard(List<ExistingBoardAssignment>, LocalDateTime)` → `int[]`
- `batchAssignToBoard(List<AssignToBoardArgs>, LocalDateTime)` → `int[]`

**핵심 설계: `IN (:demandIds)` → `= ANY(:demandIds)` 변경**

Spring `NamedParameterJdbcTemplate.batchUpdate` 는 첫 파라미터로 SQL 을 결정:
- `IN (:demandIds)` 는 리스트 크기에 따라 `?, ?, ?` 개수가 달라짐
- Batch 안 항목마다 demandIds 크기가 다르면 SQL 불일치 → 실행 실패
- `= ANY(:demandIds)` 는 PostgreSQL 배열 오퍼레이터로 **placeholder 개수 고정**
- 파라미터를 `Long[]` 배열로 전달

### 5-3. 서비스 변경

**Before:**
```java
for (ExistingBoardAssignment assignment : assignments) {
    int updated = demandRepository.assignToExistingBoard(...);  // 10 RTT
    if (updated != assignment.demandIds().size()) throw ...;
}
```

**After:**
```java
int[] updateCounts = demandBatchRepository.batchAssignToExistingBoard(assignments, now);
// 1~2 RTT
for (int i = 0; i < updateCounts.length; i++) {
    if (updateCounts[i] != assignments.get(i).demandIds().size()) throw ...;
}
```

### 5-4. 이득

Chunk 10 기준:
- 각 chunk 메서드: **10 UPDATE RTT → 1~2 RTT**
- 두 메서드 합계로 chunk 당 **~16~18 RTT 절약**

### 5-5. 왜 award 는 batchUpdate 로 안 바꿨나

`awardChunk → award()` 는 board 당 여러 SQL 실행:
- `markAwarded` (per board)
- `transitionStatusBulkByBoardIds` (list 파라미터 있지만 현재 1개씩 전달)
- `transitionStatusForBoard` (per board, winner)
- `transitionStatusBulkForBoard` (list 파라미터, losers)

**batchUpdate 대신 더 나은 최적화 여지 발견:**
- 이미 있는 list 파라미터 API 를 활용해서 **status 별로 그룹핑**
- `winningBoardIds` 와 `losingBoardIds` 분리 후 각각 한 번씩 UPDATE
- **기존 `@Modifying @Query` 그대로 재사용, JDBC 로 내려갈 필요 없음**
- 10 UPDATE → 2~4 UPDATE 로 감소

이 최적화는 아직 미적용 상태 ([남은 이슈](#7-남아있는-이슈-및-다음-단계) 참조).

---

## 6. 검토했지만 적용하지 않은 것

### 6-1. Batch bulk update (단일 SQL with CASE WHEN)

**대안:**
```sql
UPDATE demand SET demand_board_id = CASE
    WHEN id IN (1,2,3) THEN 100
    WHEN id IN (4,5,6) THEN 101
END WHERE id IN (1,2,3,4,5,6)
```

**거절 이유:**
- assignment 별 update count 개별 조회 불가 (전체 합만 반환)
- 어느 assignment 가 stale 인지 특정 불가 → all-or-nothing 만 가능
- SQL 구성 복잡, 파라미터 개수 제한 걸릴 위험
- 우리 정책 (per-assignment stale 리포트) 과 안 맞음

### 6-2. Chunk 내 SAVEPOINT 로 격리

**대안**: chunk 안에서 각 assignment 마다 SAVEPOINT → 개별 롤백 가능

**거절 이유:**
- 복잡도 급증 (JdbcTemplate 에서 SAVEPOINT 다루기 번잡)
- Existing/new board 는 all-or-nothing 이 충분히 실용적
- Substitute/award 는 BusinessException 을 chunk 내부에서 catch 하는 방식으로 대체 달성

### 6-3. saveAll + IDENTITY 로 batch INSERT

**대안**: `demandBoardRepository.saveAll(entities)` 로 batch INSERT

**거절 이유:**
- Hibernate 는 IDENTITY 엔티티의 saveAll 을 batch 로 처리 못 함 (JavaDoc 명시)
- 실질 성능은 `save()` 루프와 동일
- SEQUENCE 전환 없이는 batch 이득 불가

### 6-4. Chunk 병렬 처리

**대안**: `CompletableFuture.supplyAsync` 로 chunk 병렬 실행

**거절 이유:**
- 데드락 위험 증가 (chunk 간 락 순서 예측 불가)
- HikariCP 커넥션 풀 압박 (요청당 4 커넥션 예상 → 동시 요청 3개 = 12 커넥션 필요)
- 예외 처리 복잡도 (`CompletionException` wrapping)
- 순서 보장 필요한 경우 (newBoard clientBoardKey 매핑) 복잡
- **핵심**: 이득이 unknown, 프로파일링 필요
- 병렬화 전에 chunk 크기 증가 검토가 더 저렴

### 6-5. Stream 으로 chunk 처리 체이닝

**대안:**
```java
newBoards.stream()
    .map(nb -> save(...))
    .map(saved -> assignToBoard(...))
    .toList();
```

**거절 이유:**
- Stream 은 element 단위 lock-step 파이프라인
- INSERT/UPDATE 가 인터리브 실행됨 → 락 홀드 시간 감소 이득 상실
- 락 홀드 감소를 원하면 두 stream (또는 for 루프) 로 명시적 분리 필요

### 6-6. `NewBoardResult` 에 `FAILED` 신규 상태

**대안**: `CREATED, STALE_REJECTED, FAILED` 3 종

**거절 이유:**
- 기존 `applyExistingAssignment` 스타일과 불일치 (staleCount 에 모든 실패 흡수)
- 클라이언트 관점에서 stale/failed 구분의 실익이 명확하지 않음
- 일관성 유선

### 6-7. GroupBuy 를 `@TransactionalEventListener(AFTER_COMMIT)` 로 분리

**대안**: Spring 애플리케이션 이벤트로 GroupBuy 생성을 후속 처리

**거절 이유:**
- 커밋 직후 서버 crash 시 이벤트 유실 → GroupBuy 안 만들어짐
- 안정성 조건 (DB 저장) 부족
- **아래 outbox 대안 채택 예정** (다음 iteration)

---

## 7. 남아있는 이슈 및 다음 단계

### 7-1. 🟡 award 안의 SQL 그룹핑 최적화

**현재:**
`awardChunk → award()` 안에서 board 당 여러 UPDATE 를 sequential 실행:
- `markAwarded` — 각 board 마다 (chunk 10 = 10 UPDATE)
- `transitionStatusBulkByBoardIds` — list 파라미터인데 항상 1개씩만 전달 (10 UPDATE)
- `transitionStatusForBoard` — winner 있는 board 마다 (최대 10 UPDATE)
- `transitionStatusBulkForBoard` — loser list 이지만 board 별로 호출 (10 UPDATE)

Chunk 당 총 **30~40 UPDATE**.

**최적화 방안 (batchUpdate 불필요):**
- `awardChunk` 안에서 `winningBoardIds` / `losingBoardIds` 그룹핑
- `transitionStatusBulkByBoardIds(winningBoardIds, ASSIGNED, PAYMENT_PENDING, now)` 한 번 호출
- `transitionStatusBulkByBoardIds(losingBoardIds, ASSIGNED, FAILED, now)` 한 번 호출
- 유사하게 `markAwarded` 도 그룹핑

**예상 이득:** 10 UPDATE → 2~4 UPDATE (기존 API 재사용, SQL 중복 없음)

**미적용 이유:** 위 리팩토링에 이미 많이 다뤘고 award 는 tight coupling → 단독 iteration 필요.

---

### 7-2. 🟡 `awardChunk` 의 예상치 못한 BusinessException 처리

**현재:**
```java
case DEMAND_BOARD_NOT_FOUND,
     DEMAND_BOARD_AWARDING_INCONSISTENT,
     DEMAND_BOARD_NO_PARTICIPANT -> {
    staleRejected++;
    log.warn(...);
}
default -> throw e;  // ← 예상치 못한 코드는 chunk 롤백
```

**문제:**
- `GroupBuyService.createGroupBuy` 가 `PRODUCT_NOT_ORDERABLE` 등 다른 코드를 던지면 default 로 감
- Chunk 전체 롤백 → 다른 board 도 낭비 재시도

**개선 방안:**
- 각 case 별로 세분화된 처리
- 또는 outbox 로 groupBuy 분리 시 이 이슈 자체가 사라짐 (아래 7-4 참조)

---

### 7-3. 🟡 GroupBuy 관련 부수 쿼리 다수

**감사 결과 (완료):**
- `groupBuyService.createGroupBuy` 는 순수 DB 작업만 함 (외부 I/O 없음)
- 하지만 board 당 **6 쿼리 추가**:
  - product SELECT
  - seller SELECT
  - catalog SELECT
  - `product.startSale()` (deferred UPDATE)
  - GroupBuy INSERT (IDENTITY, 즉시 실행)
  - OutboxEvent INSERT (IDENTITY, 즉시 실행)
- Chunk 10 기준 **60 쿼리** 추가 발생

**개선 후보 (미적용):**
- N+1 SELECT 최적화: chunk 내 winnerId 전체를 한 번에 fetch
- `OutboxEvent`, `GroupBuy` SEQUENCE 전환
- `createGroupBuy` bulk API 로 재설계

---

### 7-4. 🟡 GroupBuy 를 outbox 패턴으로 분리

**제안 (다음 iteration):**
- `award()` 안에서 `createGroupBuy` 직접 호출 대신 `OutboxEvent` INSERT
- 기존 outbox 스케줄러 (`GroupBuyJudgmentOutboxPublisher`) 확장하여 새 이벤트 처리
- `CREATE_GROUP_BUY_REQUESTED` 이벤트 타입 추가

**이득:**
- `awardChunk` 트랜잭션 획기적 단축 (60 쿼리 → 10 쿼리)
- 실패 격리: award 성공 + groupBuy 실패 시나리오 정상 처리
- 재시도 자동화 (outbox 스케줄러가 처리)

**Trade-off:**
- Eventual consistency: ~1초 지연 (outbox poll 주기)
- OutboxEvent Type 확장 필요
- `createGroupBuy` idempotency 강화 필요 (재시도 안전성 → `GroupBuy.product_id UNIQUE` 제약 검토)

**결정:** 이번 리팩토링에서는 보류. 인프라 (outbox 스케줄러) 는 이미 있어서 다음 iteration 에서 낮은 비용으로 도입 가능.

---

### 7-5. 🟡 `DemandBoard` SEQUENCE 전환 재검토

**현재 결정:** 유지 (FK 종속, 이득 작음)

**언제 재검토할지:**
- Formation-plan 배치 트래픽이 유의미하게 증가
- 프로파일링에서 `createNewBoardChunk` 응답 시간이 병목으로 확인
- IDENTITY 로 인한 RTT 낭비가 실측됨

**전환 시 필요 작업:**
- Flyway 마이그레이션 (V14 예정)
- `DemandBoard` 어노테이션 변경
- `createNewBoardChunk` 에 명시적 `entityManager.flush()` 추가

---

### 7-6. 🟡 Chunk 병렬 처리 재검토

**현재 결정:** 미도입 (프로파일링 후 결정)

**필요 조건 (전부 만족 시 검토):**
- [ ] `applyFormationPlan` / `applyAwardingResult` API 응답 시간이 병목
- [ ] Chunk 처리 시간이 응답 시간의 대부분
- [ ] Chunk 크기 증가로도 해결 안 됨 (락 홀드 시간 문제)
- [ ] AI 배치가 순차 처리를 감내할 만한 여유 없음

**하나라도 No 면 병렬화 없이 지금 상태 유지.**

---

### 7-7. 🟡 미사용 코드 정리 검토

**batchUpdate 도입 후 `DemandRepository` 의 다음 메서드는 서비스에서 호출 안 됨:**
- `assignToExistingBoard(...)`
- `assignToBoard(...)`

**현재 상태:** 유지 (롤백 대비 안전 마진).

**다음 릴리스에서 확인 후 삭제 검토.**

---

### 7-8. 🟢 확실히 해결된 것

- ✅ CodeRabbit 지적 사항 3건 모두 대응 (new-board catch, awarding default count, pagination)
- ✅ Chunk 트랜잭션으로 fsync 감소 확보 (10배)
- ✅ 데드락 방어 (`chunk` 헬퍼의 Comparator 오버로드로 4개 chunk 메서드 모두 커버)
- ✅ Self-invocation 버그 수정 (`this.` → `self.`)
- ✅ `ProductAwardEvaluation` SEQUENCE 전환 (batch INSERT 활성화)
- ✅ `applyExistingAssignmentChunk`, `createNewBoardChunk` JDBC batchUpdate 도입
- ✅ `getPendingAwarding` queue-pop 패턴으로 안정화
- ✅ `groupBuyService.createGroupBuy` 감사 (외부 I/O 없음 확인)
- ✅ Chunk 메서드 이름 통일 (Chunk 접미사, 복수형)
- ✅ SubstituteOffer / Award chunk 내 BusinessException 세분화 처리

---

## 참고 문서

- [`demand-board-service-chunk-refactoring.md`](./demand-board-service-chunk-refactoring.md) — chunk 리팩토링 요약
- [`identity-to-sequence-decision.md`](../architecture/identity-to-sequence-decision.md) — SEQUENCE 전환 결정 근거
- [`nested-to-requires-new-migration-decision.md`](../architecture/nested-to-requires-new-migration-decision.md) — 이전 트랜잭션 전파 결정
- [`nested-transaction-substitute-offer-analysis.md`](./nested-transaction-substitute-offer-analysis.md) — substitute offer 트랜잭션 분석
- [`formation-plan-orphan-board-analysis.md`](./formation-plan-orphan-board-analysis.md) — 미완료 board 분석
- [`failure-handling-audit.md`](../operations/failure-handling-audit.md) — 예외 처리 감사
- [`retry-and-logging.md`](../operations/retry-and-logging.md) — 재시도 및 로깅 정책

---

## 결론

`DemandBoardService` 는 이번 리팩토링을 통해:

1. **CodeRabbit 이 지적한 실질 버그 3건 해소** (loop 중단, 카운트 누락, pagination skip)
2. **Chunk 기반 트랜잭션으로 fsync 10배 감소**
3. **JDBC batchUpdate 로 chunk 안 UPDATE RTT 10배 감소**
4. **SEQUENCE 전환으로 evaluation 대량 INSERT 최적화**
5. **데드락 방어 (Comparator 정렬)**
6. **Self-invocation 버그 발견 및 수정**

**미해결 이슈들** (award SQL 그룹핑, GroupBuy outbox 분리, DemandBoard SEQUENCE, 병렬 chunk 등) 은 프로파일링 결과 또는 다음 iteration 에서 순차적으로 진행.

프로덕션 배포 관점에서 **핵심 안정성 이슈는 모두 해소**되었고, 남은 것은 성능 최적화 여지 및 아키텍처 개선 후보들.
