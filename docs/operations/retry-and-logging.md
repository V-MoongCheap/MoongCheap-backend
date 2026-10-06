# Retry 정책 및 로깅

일시적 DB 오류(네트워크 순단, 락 타임아웃, 데드락 등)로부터 자동 복구하기 위해 **Resilience4j `@Retry`** 를 사용한다. 재시도 이벤트는 전역 리스너로 자동 로그된다.

관련 문서: [`transaction-timeout.md`](./transaction-timeout.md), [`demand-expire-batch.md`](../domain/demand-expire-batch.md)

---

## 왜 Spring Retry가 아닌 Resilience4j인가

- **Spring Boot 4.x BOM에 `spring-retry` 미포함** → 별도 버전 관리 필요
- 프로젝트에 이미 `resilience4j-spring-boot4` 의존성 존재 (OpenSearch CircuitBreaker용)
- **재시도 + 서킷브레이커 통합 관리** 가능
- 이벤트 리스너 API가 표준화되어 있어 로깅/모니터링 확장 용이

## 설정 (`application.yml`)

```yaml
resilience4j:
  retry:
    instances:
      chunkRetry:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.dao.TransientDataAccessException
          - org.springframework.dao.DataAccessResourceFailureException
```

**재시도 간격**: 1s → 2s → 4s (지수 백오프)

## 재시도되는 예외

| 예외 | 상속 관계 | 시나리오 |
|------|-----------|----------|
| `TransientDataAccessException` (base) | | 일시적 DB 오류 전반 |
| ├─ `QueryTimeoutException` | | 쿼리 타임아웃 |
| └─ `ConcurrencyFailureException` | | 데드락, 낙관적 락 충돌 |
| `DataAccessResourceFailureException` | `NonTransient...` 계열 | DB 연결 끊김 (별도 명시 필요) |

> `NonTransientDataAccessException` 계열은 재시도해도 무조건 실패하므로 **의도적으로 제외** (SQL 문법 오류, NOT NULL 위반 등).

> `BusinessException`은 재시도 대상이 아님 → 데이터 무결성 검증 실패 시 즉시 롤백.

## 적용된 위치

| 클래스 | 메서드 | 목적 |
|--------|--------|------|
| `DemandExpireChunkService` | `expireChunk` | demand 만료 배치 청크 |
| `DemandBoardCancelChunkService` | `cancelChunk` | demand board 취소 배치 청크 |
| `DemandBoardService` | `applyFormationPlan` | AI 편성 계획 반영 |

모두 `@Retry(name = "chunkRetry")` + `@Transactional` 조합.

### Aspect 순서

`@Retry` (outer) → `@Transactional` (inner) 순으로 감싸야 트랜잭션 롤백 후 새 트랜잭션으로 재시도된다. Resilience4j `@Retry`는 기본 order가 낮아(=outer) 자동으로 이 순서가 보장된다.

## 로깅

### 문제

`@Retry`는 기본적으로 **아무 로그도 남기지 않는다.** 최종 실패 시 예외만 propagate되어, 운영 중 재시도가 발생했는지 파악 불가.

### 해결: 전역 이벤트 리스너

`common/config/RetryLoggingConfig.java`

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RetryLoggingConfig {

    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerListeners() {
        retryRegistry.getEventPublisher()
            .onEntryAdded(entryEvent -> {
                var retry = entryEvent.getAddedEntry();
                retry.getEventPublisher()
                    .onRetry(event -> log.warn(...))
                    .onError(event -> log.error(...))
                    .onSuccess(event -> log.info(...));
            });
    }
}
```

**동작**: `RetryRegistry`에 새 retry 인스턴스가 추가될 때마다 자동으로 리스너 부착. 앞으로 추가되는 모든 `@Retry`에 자동 적용됨.

### 로그 이벤트

| 레벨 | 이벤트 | 발화 조건 | 예시 |
|------|--------|-----------|------|
| `WARN` | `onRetry` | 실패 후 재시도 시 (각 시도) | `Retry attempt: name=chunkRetry, attempt=1, lastException=...` |
| `INFO` | `onSuccess` | 재시도 후 성공 시 | `Retry succeeded after failures: name=chunkRetry, attempts=2` |
| `ERROR` | `onError` | 모든 재시도 소진 시 | `Retry exhausted: name=chunkRetry, attempts=3, cause=...` |
| — | `onIgnoredError` | retry-exceptions에 안 걸리는 예외 | (미사용) |

> **첫 시도에 성공 시 이벤트 미발화** — hot path 오버헤드 0.

### 오버헤드

- `@Configuration` bean은 singleton, `@PostConstruct` 1회 실행
- 리스너는 실제 이벤트 발생 시에만 트리거
- 정상 처리(첫 시도 성공)에서는 리스너 미호출

## 새 `@Retry` 인스턴스 추가 방법

1. `application.yml`의 `resilience4j.retry.instances`에 이름 추가
2. 대상 메서드에 `@Retry(name = "새이름")` 부착
3. 로깅은 자동 적용 (별도 작업 불필요)

## 모니터링 항목

Prod 로그에서 아래 패턴 주기적 확인:

- `Retry attempt` 빈도 급증 → DB 상태 이상 신호
- `Retry exhausted` 발생 → 알림 대상, 트랜잭션 타임아웃/청크 크기 재검토
- `Retry succeeded after failures` 다수 → 자동 복구 정상 동작 확인

## 관련 문서

- [`transaction-timeout.md`](./transaction-timeout.md) — 트랜잭션·락 타임아웃 방어 정책
- [`demand-expire-batch.md`](../domain/demand-expire-batch.md) — Retry가 적용된 배치 예시
