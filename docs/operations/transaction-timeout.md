# 트랜잭션 · 락 타임아웃 정책

배치(Advisory Lock을 잡는 스케줄러)가 hang 되었을 때 락을 무한히 점유하는 사고를 방지하기 위한 다중 방어선 설정.

## 배경

`DemandExpireScheduler` 등 배치 작업은 Advisory Lock으로 다중 인스턴스 중복 실행을 막는다. Advisory Lock은 **트랜잭션이 살아있는 동안** 유지되므로, 아래 상황에서 락이 무한 점유될 수 있다:

- 앱은 살아있는데 UPDATE 쿼리가 응답 없음
- 트랜잭션은 열려 있는데 다음 쿼리를 안 보냄
- JDBC 커넥션이 강제 종료되지 않고 idle 상태로 남음

락이 무한 점유되면 **다음 실행 주기마다 모든 인스턴스가 skip** 되어 배치가 사실상 정지된다. 알림도 없이 조용히 실패하므로 방어책이 필수.

## 방어 계층

| 계층 | 설정 위치 | 값 | 커버 범위 |
|---|---|---|---|
| 1. 청크 트랜잭션 `timeout` | `DemandExpireChunkService` | 10s | 청크 단위 hang |
| 2. 스케줄러 전체 `MAX_RUNTIME_MS` | `DemandExpireScheduler` | 300s | 배치 전체 오버런 |
| 3. 스케줄러 청크 상한 `MAX_CHUNKS` | `DemandExpireScheduler` | 100 | 무한 루프 (버그 방어) |
| 4. `idle_in_transaction_session_timeout` | `application-prod.yml` (HikariCP) | 60s | 모든 트랜잭션의 idle 상태 |
| 5. `leak-detection-threshold` | `application-prod.yml` (HikariCP) | 30s | 관측 로그 (예방X, 감지O) |

### 1. 청크 트랜잭션 `@Transactional(REQUIRES_NEW, timeout = 10)`

배치는 청크 단위로 분할되며 청크마다 새 트랜잭션을 연다.

```java
@Service
public class DemandExpireChunkService {
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<Integer> expireChunk(LocalDateTime threshold, int chunkSize) {
        if (!advisoryLockAdaptor.tryAcquireXactLock(...)) return Optional.empty();
        return Optional.of(demandRepository.expireChunk(threshold, chunkSize));
    }
}
```

- 청크당 최대 10초 → 초과 시 `TransactionTimedOutException` → 롤백 → Advisory Lock 해제
- Advisory Lock이 청크 트랜잭션 스코프에 묶여 있어, 청크 종료 즉시 다음 인스턴스가 잡을 수 있음
- 정상 청크(1000건)는 수백 ms~수 초 안에 완료

### 2. 스케줄러 `MAX_RUNTIME_MS = 300_000` (5분)

```java
while (chunks < MAX_CHUNKS
    && System.currentTimeMillis() - start < MAX_RUNTIME_MS) {
    ...
}
```

- 배치 전체가 5분 넘게 돌면 강제 중단
- 매시간 배치라 다음 정각과 겹치지 않도록 여유
- 처리 못한 대상은 다음 실행에서 이어서 처리 (idempotent)

### 3. `MAX_CHUNKS = 100`

- 최대 100 × 1000 = 10만 건/실행 상한
- 무한 루프 버그 방지용 안전장치

### 4. `idle_in_transaction_session_timeout = 60000` (ms)

`application-prod.yml`의 HikariCP `connection-init-sql`로 세션 시작 시 설정.

```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: "SET idle_in_transaction_session_timeout = '60000'"
```

- **트랜잭션이 열린 채 60초 동안 쿼리가 없으면** PostgreSQL이 세션 강제 종료
- 앱이 트랜잭션 시작 후 hang 된 시나리오 대응
- 세션 종료 시 트랜잭션 롤백 + Advisory Lock 해제

### 5. `leak-detection-threshold = 30000` (ms)

```yaml
hikari:
  leak-detection-threshold: 30000
```

- 커넥션을 30초 이상 풀에 반환하지 않으면 warning 로그
- 락 자체를 풀지는 않지만 이상 상황을 조기 감지
- Prod 로그 모니터링과 연계 (예: WARN 이상 알림)

## 시간대 (`ZoneId`) 처리

`@Scheduled(zone = "Asia/Seoul")`과 `LocalDateTime.now(ZONE_SEOUL)`를 함께 사용해 배포 환경(컨테이너·클라우드 UTC 등)의 JVM 기본 TZ에 영향받지 않도록 명시.

```java
private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

@Scheduled(cron = "...", zone = "Asia/Seoul")
public void expireOverdueUnassigned() {
    LocalDateTime threshold = LocalDateTime.now(ZONE_SEOUL);
    ...
}
```

## 커버되지 않는 케이스

- **DB 자체가 응답 없음**: 위 계층 모두 무력. DB 헬스체크·모니터링 별도 필요.
- **`@Transactional` 미부착 배치**: `timeout` 옵션이 무효. 배치 추가 시 반드시 `@Transactional`도 함께.
- **청크당 10초를 초과하는 대량 청크**: `CHUNK_SIZE`를 낮추거나 `timeout`을 올리기.

## 배치 신규 추가 시 체크리스트

1. `@Scheduled` + 청크 서비스 분리 + `@Transactional(REQUIRES_NEW, timeout = N)` 조합
2. Advisory Lock 사용 시 `tryAcquireXactLock`로 non-blocking 획득
3. 락 키는 `AdvisoryLockKeys`에 상수/메서드로 정의
4. 청크 반환은 `Optional<Integer>` (락 미획득 = empty, 처리 건수 = value)
5. 시간 비교 시 `LocalDateTime.now(ZONE_SEOUL)` 등 TZ 명시
6. `MAX_CHUNKS`·`MAX_RUNTIME_MS` 등 상한 명시
7. 처리 건수·청크 수·소요 시간 `log.info`로 기록

## 참고

- Advisory Lock 해제 시점 정리
  - `COMMIT` / `ROLLBACK` → 해제
  - `TransactionTimedOutException` → 롤백 → 해제
  - `idle_in_transaction_session_timeout` 초과 → 세션 종료 → 해제
  - 앱 프로세스 kill → 커넥션 종료 → 해제
  - **앱 hang (프로세스 살아있음)** → 위 계층으로 방어
- 로컬(`application-local.yml`)에는 HikariCP 설정 미적용. 개발 편의를 위해 기본값 유지.
