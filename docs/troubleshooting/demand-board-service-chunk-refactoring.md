# DemandBoardService Chunk 리팩토링 및 평가

## 배경

내부 배치 API 4종 (`/api/*/internal/*`)에서 발생하던 다음 문제들을 해결하기 위해 chunk 기반 all-or-nothing 트랜잭션 구조로 재설계.

### 리팩토링 전의 문제
- `applyFormationPlan`의 new board 루프가 `StaleFormationItemException`만 catch → `DataAccessException`/`RuntimeException` 발생 시 루프 중단, 이전 처리분은 커밋된 채 caller에게 5xx 반환
- `applyAwardingResult`의 default catch 블록이 `staleRejectedCount` 증가 안 함 → 응답 카운트 불일치
- `getPendingAwarding`이 offset 기반 페이지네이션이라 처리 후 재조회 시 skip 발생
- 각 assignment마다 REQUIRES_NEW 트랜잭션 → COMMIT/fsync 횟수가 항목 수만큼 발생

---

## 적용된 리팩토링

### 1. Chunk 기반 트랜잭션 구조

- 항목 리스트를 chunk 단위(기본 10개)로 나눠 처리
- 각 chunk가 REQUIRES_NEW 트랜잭션 → chunk당 fsync 1회
- Chunk 내 실패 시 chunk 전체 롤백 (all-or-nothing)

**적용 위치:**
- `applyExistingAssignmentChunk` — 기존 board에 demand 배정
- `createNewBoardChunk` — 새 board 생성 및 demand 배정
- `substituteOfferChunk` — 대체 상품 오퍼 적용
- `awardChunk` — AI 낙찰 결과 반영

### 2. 정렬을 통한 데드락 방어

- Concurrent chunk 간 데드락 회피를 위해 락 획득 순서를 오름차순으로 고정
- `chunk` 헬퍼 오버로드로 `Comparator`를 받도록 확장:
  ```java
  chunk(list, size, Comparator.comparing(...))
  ```
- Caller별 정렬 키:
  - `ExistingBoardAssignment` → `demandBoardId`
  - `NewBoard` → `Collections.min(demandIds)`
  - `Proposal` → `demandId`
  - `BoardResult` → `boardId`

### 3. 에러 처리 세분화

**Chunk 내부 (BusinessException):**
- 도메인 stale 코드 (예: `NOT_FOUND`, `NOT_ELIGIBLE`, `AWARDING_INCONSISTENT`) → `staleRejected++` 후 continue
- 특수 no-op (`ALREADY_APPLIED`) → `alreadyApplied++` 후 continue
- 예상치 못한 코드 → `throw e` (chunk 롤백)

**외부 catch (트랜잭션 broken):**
- `DataAccessException`, `RuntimeException` → chunk 전체 stale로 카운트
- DB aborted state / rollback-only marking에 안전하게 대응

### 4. Self-invocation 보정

- `@Transactional(REQUIRES_NEW)` 메서드는 반드시 `self.` 통해 호출
- `this.createNewBoardChunk(...)` → `self.createNewBoardChunk(...)` 수정 (이전 버그)

### 5. 페이지네이션 방식 변경

`getPendingAwarding`을 offset 기반 → **queue-pop 패턴**으로 변경:
- `LIMIT :size` (OFFSET 없음)
- 처리된 board는 `GB_AWARDING`에서 빠지므로 재조회 시 자연스럽게 다음 대상
- 실패한 board는 그대로 남아 자동 재시도
- 응답에 `hasNext` 유지, `page` 필드 제거

### 6. 기타

- `NewBoardStatus`에 `STALE_REJECTED` 분류 유지 (별도 `FAILED` 신설하지 않음, existing/awarding 스타일과 통일)
- `EXISTING_ASSIGNMENT_CHUNK_SIZE` → `FORMATION_PLAN_CHUNK_SIZE`로 이름 변경
- `applyExistingAssignment` → `applyExistingAssignmentChunk`로 이름 변경 (복수형 대칭)
- `chunk` 헬퍼가 내부에서 `nullSafe`를 호출하도록 통합

---

## 코드 평가

### 🟢 Chunk 커버리지

내부 write API 3개 (`formation-plans`, `substitute-offer-plans`, `internal/result`) 모두 chunk 적용됨.
Read API (`/pending`)는 queue-pop 방식으로 처리. **빠진 곳 없음.**

### 🟢 Lock / 데드락

- 모든 chunk 메서드가 caller에서 `Comparator`로 정렬된 리스트를 받음
- Concurrent transaction들이 항상 오름차순으로 락 획득 → circular wait 불가
- `assignToBoard`/`assignToExistingBoard` SQL 자체도 `ORDER BY id FOR UPDATE` 사용

### 🟢 fsync 최적화

- 이전: 항목 N개 = fsync N회
- 지금: chunk당 fsync 1회 = fsync N/chunk_size 회 (10배 감소)

### 🟡 남아있는 이슈

#### 1. `groupBuyService.createGroupBuy` 예외 fall-through

`awardChunk`의 switch에서:
```java
default -> throw e;  // ← groupBuyService의 PRODUCT_NOT_ORDERABLE 등
```

- CodeRabbit이 지적했던 케이스: `GroupBuyService.createGroupBuy` → `ProductPublicService.getByIdAndStatus` → `PRODUCT_NOT_ORDERABLE`
- 현재 처리: chunk 전체 롤백 → `staleRejectedCount += chunkSize`
- 개선 방안: `default`에서 해당 코드 명시적 catch 후 board 단위 격리 (substituteOfferChunk 패턴 참고)

#### 2. IDENTITY 전략의 batch INSERT 불가

- `DemandBoard`, `ProductAwardEvaluation`이 `@GeneratedValue(IDENTITY)`
- Hibernate가 batch INSERT 못 함 → save당 RTT 1회
- 개선 방안: SEQUENCE 전략으로 전환 (PostgreSQL만 지원, 스키마 마이그레이션 필요)
- **투자 대비 이득 판단 필요** — 트래픽이 커진 후에 검토

#### 3. Chunk 내 처리는 여전히 순차

- 각 chunk 안에서 for 루프로 순차 처리
- RTT 관점에선 N개 UPDATE = N RTT (batch update 미사용)
- 개선 방안:
  - JDBC batchUpdate 도입 (Spring Data JPA `@Modifying` 벗어나 `NamedParameterJdbcTemplate` 사용)
  - RTT 감소만큼의 이득 있지만 SQL 중복 관리 부담
  - **DB가 로컬에 가까우면 이득 미미, 원격이면 유의미**

#### 4. Chunk 병렬 처리 미도입

- 한 요청 안의 여러 chunk가 순차 실행
- 대량 요청 시 총 처리 시간 = chunk 개수 × chunk 시간
- 병렬화 가능하지만:
  - 데드락 위험 증가
  - 커넥션 풀 압박
  - 트랜잭션 관리 복잡
- **프로파일링 후 병목으로 확인되면 도입 고려**

#### 5. 트랜잭션 안의 외부 호출

- `awardChunk` → `award` → `groupBuyService.createGroupBuy`
- 만약 `createGroupBuy` 안에서 HTTP 호출 등 외부 I/O가 있다면 chunk 트랜잭션 시간이 폭증
- **감사 필요**: `GroupBuyService.createGroupBuy`의 실제 동작

### 🟢 에러 방어 커버리지

| 예외 종류 | 처리 방식 |
|---|---|
| 도메인 stale (`NOT_FOUND`, `NOT_ELIGIBLE` 등) | Chunk 안에서 `staleRejected++` 후 continue |
| `ALREADY_APPLIED` (substitute) | Chunk 안에서 `alreadyApplied++` 후 continue |
| 예상치 못한 BusinessException | Chunk 롤백 → 외부에서 stale 카운트 |
| `DataAccessException` | Chunk 롤백 → 외부에서 stale 카운트 |
| `RuntimeException` | Chunk 롤백 → 외부에서 stale 카운트 |

- 모든 예외 경로가 caller에게 200 응답 + 정확한 카운트로 반환
- 클라이언트(AI 배치)가 재시도 필요한 항목을 판단할 수 있음

### 🟢 Idempotency

- `substituteOffer`는 `ALREADY_APPLIED` 명시 처리
- 다른 곳은 상태 조건부 UPDATE (`WHERE status = ...`)로 자연스러운 idempotency
- 재시도 안전

---

## 우선순위별 액션 아이템

### 🔴 즉시 수정 필요 없음
현재 리팩토링으로 지적됐던 🔴 이슈들 (정렬 누락, self-invocation, log 오타)은 모두 해결됨.

### 🟡 곧 처리
1. `awardChunk`의 `default -> throw e`에서 `groupBuyService` 예외 코드 세분 처리 검토
2. `GroupBuyService.createGroupBuy` 내부 로직 감사 (외부 I/O 여부)

### 🟢 장기 검토
3. `DemandBoard`, `ProductAwardEvaluation` ID 전략을 SEQUENCE로 전환 (batch INSERT 이득)
4. Chunk 병렬 처리 도입 여부 (트래픽 프로파일링 후 결정)
5. JDBC batchUpdate 도입 여부 (DB 원격도에 따라 결정)

---

## 참고 결정 문서

- `nested-to-requires-new-migration-decision.md`: 트랜잭션 propagation 결정
- `nested-transaction-substitute-offer-analysis.md`: substitute offer 트랜잭션 구조 분석
- `formation-plan-orphan-board-analysis.md`: 미완료 board 분석
- `failure-handling-audit.md`: 예외 처리 감사
- `retry-and-logging.md`: 재시도 및 로깅 정책
