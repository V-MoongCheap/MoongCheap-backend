# Demand 만료 배치 (UNASSIGNED → EXPIRED)

`desire_end_at`이 지난 UNASSIGNED 상태의 demand를 EXPIRED로 일괄 전환하는 스케줄러 배치.

## 전체적으로 왜가 부족함, 이걸 선택한 명확한 이유

## 배경

`demand.status`가 `UNASSIGNED`인 상태로 `desire_end_at`이 지나면 실제로는 만료 상태이지만 DB에는 반영되어 있지 않다. 사용자 입장에서는 이미
지난 요청이 여전히 "진행 중"으로 보이고, `uq_demand_member_catalog_active`(진행 중 상태 UNIQUE) 제약 때문에 같은 카탈로그로 새 수요를
등록하려 하면 409가 발생한다.

이 문제를 주기적 배치로 해소한다.

## 접근 방식 결정

### Batch vs Lazy

Lazy(조회 시 판정) 방식과 Batch 중 **Batch만 사용**하기로 결정.

| 방식                      | 채택? | 이유                                                                       |
|-------------------------|-----|--------------------------------------------------------------------------|
| **Batch만**              | ✅   | 단순, 관측 쉬움, idempotent UPDATE로 안전                                         |
| **Lazy — 표시만 EXPIRED로** | ❌   | DB 상태와 응답 불일치. `uq_demand_member_catalog_active` 제약이 여전히 걸려 재등록 시 409 발생 |
| **Lazy — 조회 시 UPDATE**  | ❌   | 읽기 경로에 write, 락·트랜잭션 관리 복잡. 얻는 이득(최대 1시간 UX 지연 단축)에 비해 비용 큼              |

Batch 단독 사용의 유일한 부작용: **배치 주기 사이에 만료된 수요가 UNASSIGNED로 보이며 재등록 시 409**. 이는 흔치 않은 edge case로 판단하고 감수.

### 배치 주기

**`0 5 * * * *`** (매시 5분).

- desire_end_at 기본값은 `now + 7일`이라 정각 만료 대기 시나리오 없음 → 매시간 충분
- 매분 실행은 대부분 "0건 처리" 로그만 쌓임 → 관측 노이즈
- 일 1회는 재등록 창(window)이 하루로 커져 부담
- 정각(0분)은 다른 배치와 겹칠 우려 → **5분 오프셋**

주기 선택 비교:

| 주기            | 언제 적합                        | 이 배치에 적합?               |
|---------------|------------------------------|-------------------------|
| **매분**        | 결제·주문처럼 초·분 단위 SLA           | ❌ 오버킬. 대부분 0건 처리 로그만 쌓임 |
| **15분**       | 만료 UX가 중요하거나 재등록 창을 줄이고 싶을 때 | ⚠️ 이 배치엔 굳이             |
| **매시간**       | 이 케이스에 가장 적합                 | ✅ 채택                    |
| **일 1회 (새벽)** | 정말 시간 여유 있는 정리성 데이터          | ❌ 재등록 창이 하루로 너무 큼       |

DB 부하는 UPDATE 1개라 밀리초 수준 → 주기 결정에 사실상 영향 없음. 관측 노이즈와 재등록 창 크기가 주 판단 기준.

### 다중 인스턴스 방어: Advisory Lock

**ShedLock 대신 PostgreSQL Advisory Lock 채택.**

| 기준         | Advisory Lock  | ShedLock              |
|------------|----------------|-----------------------|
| 인프라 오버헤드   | 없음 (PG 내장)     | 테이블 필요                |
| 코드량        | 5줄 이내          | 라이브러리 + 어노테이션         |
| 관측성        | `pg_locks` 뷰   | 락 테이블 (`locked_by` 등) |
| DB 종속성     | PG 전용          | 다중 provider 지원        |
| 이 프로젝트 적합도 | ✅ PG 확정, 배치 소수 | 오버킬                   |

이미 프로젝트에 `AdvisoryLockAdaptor`가 있으므로 재사용.

### Non-blocking 락: `pg_try_advisory_xact_lock`

**`try_` 계열 사용 — timeout 개념 자체가 없음.**

- 락 획득 실패 시 즉시 `false` 반환 → 대기 없이 이 실행 스킵
- 배치는 다음 주기에 다시 시도하면 되므로 대기 불필요
- Blocking 버전(`pg_advisory_xact_lock`) 사용 시 커넥션 풀 낭비·인터리브 문제 발생

### 청크 분할

**서브쿼리 + LIMIT + FOR UPDATE SKIP LOCKED** 패턴으로 청크 UPDATE.

- PostgreSQL은 `UPDATE ... LIMIT` 직접 미지원 → 서브쿼리로 우회
- `FOR UPDATE SKIP LOCKED`: 다른 세션이 잡은 row는 스킵 → 인터리브 시에도 대기·데드락 없음
- 청크마다 별도 트랜잭션(`REQUIRES_NEW`)으로 락·WAL 부담 분산

MVP 볼륨엔 청크 없이도 되지만, 향후 확장 대비하여 처음부터 청크 구조로 설계.

### 청크 결과 반환: `Optional<Integer>`

매직 넘버(`-1` = 락 미획득) 대신 `Optional`.

- `empty` → 락 미획득 (다른 인스턴스가 실행 중)
- `Integer` → 실제 처리 건수

호출부에서 `isEmpty()` 체크로 의도가 명확.

## 구현

### 클래스 구조

```
demand/application/demand/
├── DemandExpireScheduler.java      — 오케스트레이션 (스케줄러 트리거, 루프, 로깅)
└── DemandExpireChunkService.java   — 청크 단위 트랜잭션 + 락 획득
```

**분리 이유**: 스케줄러 자체는 트랜잭션 없이 while 루프만 담당. 각 청크가 자체 트랜잭션(`REQUIRES_NEW`)을 열어야 락·WAL이 청크 단위로 해제됨.
`DemandService`(사용자 요청용)와도 분리 — 트랜잭션 시맨틱이 다르고 SRP 위반 방지.

### Repository (`DemandRepository.java`)

```java

@Modifying
@Query(value = """
    UPDATE demand
       SET status = 'EXPIRED', processed_at = :threshold
     WHERE id IN (
         SELECT id FROM demand
          WHERE status = 'UNASSIGNED' AND desire_end_at < :threshold
          LIMIT :chunkSize
          FOR UPDATE SKIP LOCKED
     )
    """, nativeQuery = true)
int expireChunk(
    @Param("threshold") LocalDateTime threshold,
    @Param("chunkSize") int chunkSize);
```

- `WHERE status = 'UNASSIGNED'` — idempotent 보장 (여러 번 실행돼도 이미 EXPIRED된 건 다시 안 건드림)
- native query — JPQL은 서브쿼리 + LIMIT + FOR UPDATE 조합 지원 불완전
- 상태 문자열 하드코딩 — enum 이름 변경 시 함께 수정 필요

### Advisory Lock

**`AdvisoryLockKeys.java`**

```java
public static final String DEMAND_EXPIRE_BATCH = "batch:demand-expire";
```

**`AdvisoryLockAdaptor.tryAcquireXactLock`** (기존 확장)

```java
public boolean tryAcquireXactLock(String lockKey) {
    Boolean acquired = (Boolean) em.createNativeQuery(
            "SELECT pg_try_advisory_xact_lock("
                + "('x' || substr(md5(:k), 1, 16))::bit(64)::bigint)")
        .setParameter("k", lockKey)
        .getSingleResult();
    return Boolean.TRUE.equals(acquired);
}
```

- non-blocking, 즉시 반환
- 트랜잭션 스코프 → 커밋/롤백 시 자동 해제

### ChunkService (`DemandExpireChunkService.java`)

```java

@Service
@RequiredArgsConstructor
public class DemandExpireChunkService {

    private final AdvisoryLockAdaptor advisoryLockAdaptor;
    private final DemandRepository demandRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<Integer> expireChunk(LocalDateTime threshold, int chunkSize) {
        if (!advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_EXPIRE_BATCH)) {
            return Optional.empty();
        }
        return Optional.of(demandRepository.expireChunk(threshold, chunkSize));
    }
}
```

- `REQUIRES_NEW` — 스케줄러 루프와 독립 트랜잭션
- `timeout = 10` — 청크당 10초 초과 시 `TransactionTimedOutException` → 롤백 → 락 해제
- 락 미획득 → `Optional.empty()` 반환, 호출부가 스케줄러 전체 중단

### Scheduler (`DemandExpireScheduler.java`)

```java

@Slf4j
@Component
@RequiredArgsConstructor
public class DemandExpireScheduler {

    private static final int CHUNK_SIZE = 1000;
    private static final int MAX_CHUNKS = 100;
    private static final long MAX_RUNTIME_MS = 300_000;
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final DemandExpireChunkService chunkService;

    @Scheduled(cron = "${moongcheap.batch.demand-expire.cron:0 5 * * * *}", zone = "Asia/Seoul")
    public void expireOverdueUnassigned() {
        LocalDateTime threshold = LocalDateTime.now(ZONE_SEOUL);
        long start = System.currentTimeMillis();
        int total = 0;
        int chunks = 0;

        while (chunks < MAX_CHUNKS
            && System.currentTimeMillis() - start < MAX_RUNTIME_MS) {
            Optional<Integer> result = chunkService.expireChunk(threshold, CHUNK_SIZE);

            if (result.isEmpty()) {
                log.info("Demand expiration batch preempted: lock held elsewhere, total={}", total);
                return;
            }
            int updated = result.get();
            total += updated;
            chunks++;
            if (updated == 0 || updated < CHUNK_SIZE) {
                break;
            }
        }

        log.info("Demand expiration batch finished: updated={}, chunks={}, elapsedMs={}",
            total, chunks, System.currentTimeMillis() - start);
    }
}
```

주요 결정:

- **스케줄러 자체엔 `@Transactional` 없음** — 청크가 자체 트랜잭션
- **`threshold` 실행당 1회 고정** — 청크간 시간 드리프트 방지
- **`while` 채택 (for 아님)** — 아래 설명
- **`ZoneId.of("Asia/Seoul")`** — 컨테이너 기본 UTC 환경 방어
- **`MAX_CHUNKS`, `MAX_RUNTIME_MS`** 이중 상한

#### `while` vs `for` 판단

이 루프는 **복합 종료 조건 (개수 AND 시간)** 을 가진다:

```java
while(chunks<MAX_CHUNKS
    &&System.

currentTimeMillis() -start<MAX_RUNTIME_MS){...}
```

`for`로 표현하면 시간 조건이 헤더에서 분리되어 오히려 덜 명확해진다:

```java
for(int chunks = 0;
chunks<MAX_CHUNKS;chunks++){
    if(System.

currentTimeMillis() -start >=MAX_RUNTIME_MS)break;
    ...
    }
```

일반적 지침:

- **단일 조건** (예: `for i < MAX`) → `for`가 짧고 명확
- **복합 조건 (개수 + 시간 + 상태 등)** → `while`이 자연스러움
- **처리할 게 있는 동안 계속** → `while`

이 배치는 두 번째 케이스라 `while` 채택. `MAX_CHUNKS`는 무한 루프 방어용 안전장치, `MAX_RUNTIME_MS`는 정각 오버런 방어용 — 각각 목적이 다른
상한이라 하나로 합쳐 for에 넣기 어려움.

### 설정 (`application.yml`)

```yaml
moongcheap:
  batch:
    demand-expire:
      cron: "0 5 * * * *"
```

### `@EnableScheduling` (`MoongCheapBackendApplication.java`)

```java

@SpringBootApplication
@EnableScheduling
public class MoongCheapBackendApplication { ...
}
```

## 다중 인스턴스 동작

### 정상 시나리오 (동시 트리거)

```
t=0     A, B 동시에 @Scheduled 트리거
t=1ms   A, B 각자 expireChunk() 호출
t=2ms   A: tryAcquireXactLock → ✅
        B: tryAcquireXactLock → ❌ (A가 잡음)
t=3ms   B: Optional.empty 반환 → 스케줄러 "preempted" 로그 → return
t=50ms  A: chunk1 커밋, 락 해제
t=51ms  A: 루프 계속, chunk2 시도 → ✅
...
A만 모든 청크 처리
```

**한 인스턴스가 전부 처리, 다른 인스턴스는 즉시 종료.** 인터리브 없음.

### 지연 트리거 (드묾)

- A가 실행 중, GC/네트워크 지연으로 B가 늦게 트리거
- B가 트리거 시점에 A가 청크 사이(락 해제 순간)라면 B가 락 잡을 수 있음
- 다음 청크에서 A는 락 획득 실패 → A도 return
- **A → B 핸드오프** 발생 (인터리브 아님)

데이터 무결성엔 영향 없음 (UPDATE idempotent).

## 방어 계층

배치가 hang 되어 락을 무한 점유하는 사고를 다중 계층으로 방어. 자세한 내용은 [`transaction-timeout.md`](../operations/transaction-timeout.md)
참고.

요약:

| 계층                                    | 값    | 목적         |
|---------------------------------------|------|------------|
| 청크 `@Transactional(timeout)`          | 10s  | 청크 단위 hang |
| 스케줄러 `MAX_RUNTIME_MS`                 | 300s | 배치 전체 오버런  |
| 스케줄러 `MAX_CHUNKS`                     | 100  | 무한 루프 방어   |
| `idle_in_transaction_session_timeout` | 60s  | 트랜잭션 idle  |
| HikariCP `leak-detection-threshold`   | 30s  | 관측용        |

## 관측

배치 실행마다 `log.info`로 아래 남김:

- **정상 완료**: `updated={총건수}, chunks={처리청크수}, elapsedMs={소요시간}`
- **락 미획득으로 중단**: `preempted: lock held elsewhere, total={그때까지처리한건수}`

Prod 로그 모니터링 시 확인 항목:

- `updated`가 갑자기 급증 → 배치가 이전에 도는지 확인 (락 문제로 skip되고 있었을 가능성)
- `elapsedMs` 증가 추세 → 청크 크기 재검토
- `preempted` 로그 빈도 → 다중 인스턴스 실행 여부 확인

## 확장 시 지침

다른 상태 전환 배치가 필요할 경우 (예: `SUBSTITUTE_OFFERED`에서 응답 없으면 `EXPIRED`):

1. **Repository에 별도 native query 추가**
    - 유사한 서브쿼리 + LIMIT + FOR UPDATE SKIP LOCKED 패턴
2. **ChunkService 분리 or 별도 서비스**
    - 트랜잭션 설정(`REQUIRES_NEW, timeout`) 재사용
3. **Advisory Lock 키 신설**
    - `AdvisoryLockKeys`에 `SUBSTITUTE_OFFER_EXPIRE_BATCH` 등 상수
4. **Scheduler는 별도 클래스**
    - 배치별 상한·주기가 다를 수 있으므로

배치 신규 추가 시 체크리스트는 `transaction-timeout.md`의 "배치 신규 추가 시 체크리스트" 섹션 참고.

## 결정을 미룬 사항

- **ShedLock 도입**: 현재 배치가 소수라 Advisory Lock으로 충분. 배치가 늘어나거나 관측 요구가 강해지면 그때 검토.
- **청크 크기 튜닝**: `CHUNK_SIZE = 1000`은 관례적 값. 실제 elapsedMs 로그 관찰 후 조정.
- **`MAX_RUNTIME_MS` 튜닝**: 5분은 매시간 배치엔 여유. 실제 볼륨 보고 3분·10분 등으로 조정 가능.
- **알림/이벤트 발행**: 만료 시 사용자 알림 필요 여부는 도메인 결정. 필요하면 배치 안에 이벤트 발행 추가.

## 관련 문서

- [`transaction-timeout.md`](../operations/transaction-timeout.md) — 트랜잭션/락 타임아웃 방어 정책
- [`api-error-responses.md`](../api/api-error-responses.md) — 에러 코드 정의
