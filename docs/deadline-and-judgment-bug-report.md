# 보드·수요 조기 마감 및 공동구매 판정 누락 수정 보고서

- 작성일: 2026-09-27
- 기준: 사용자 장애 제보, 수정 과정의 논의, 현재 작업 트리의 코드
- 상태: 코드 수정 및 로컬 검증 완료. 운영 배포와 운영 데이터 복구 완료 여부는 확인하지 않음.

## 1. 장애 현상과 영향

| 구분 | 제보된 현상 | 영향 |
| --- | --- | --- |
| 보드 #5 | 마감 9/27 03:01 KST로 설정했으나 9/26 21:02 취소 배치 처리 | 참여 수요 #16이 `FAILED`로 전환 |
| 보드 조회 | API의 `saleEndAt`은 03:01로 표시 | 조회 화면과 배치 판단이 불일치 |
| 수요 #2 | 희망 마감 9/27 15:58에 대해 06:58부터 조기 만료될 가능성 제보 | 수요 만료에도 같은 시간대 문제 우려 |
| 공동구매 #3 | 마감 9/26 18:34:59 이후 18:40:01에 `Removing stale group-buy judgment: groupBuyId=3` 기록 | 판정·자동결제가 진행되지 않고 주문이 결제대기에 남음 |
| 공동구매 #1·#2 | 동일한 stale 예약 삭제 로그 제보 | 판정 누락 가능성 |

보드 #5의 제보된 두 시각 차이는 약 5시간 59분이다. 따라서 이 사례의 처리 시각 자체를 정확히 “9시간 전 실행”으로 단정하지 않는다. 9시간은 KST와 UTC의 차이이며, 배치 실행 주기에 따라 실제 처리 시각은 달라질 수 있다. 수요 #2의 조기 만료 시각은 당시 예상이며 실제 처리 기록으로 확인한 결과가 아니다.

## 2. 원인 분석

### 2.1 보드·수요 마감 비교의 시간대 경계

보드 `sale_end_at`과 수요 `desire_end_at`은 `TIMESTAMPTZ` 컬럼이다. 기존 스케줄러는 KST `LocalDateTime`을 만들어 네이티브 쿼리의 비교 인자로 전달했다.

```sql
sale_end_at < :threshold
desire_end_at < :threshold
```

`LocalDateTime`에는 시간대 정보가 없다. JVM 기본 시간대, JDBC 바인딩, DB 세션 설정에 따라 이 값을 절대시각으로 해석하는 경계에서 불일치가 발생할 수 있었다. API 조회에는 `JdbcTimeMapper`의 KST 변환 경로도 있어, 화면 표시가 정상이라는 사실만으로 배치 비교까지 정상이라고 볼 수 없었다.

운영 JVM·DB 설정과 실제 바인딩 값을 직접 수집해 9시간 차이를 재현한 것은 아니다. 제보와 코드에서 확인한 시간대 불일치 가능성을 바탕으로, 문제가 되는 비교 인자 변환 경로를 제거했다.

### 2.2 공동구매 후보 선택과 판정 서비스의 시간 기준 불일치

공동구매 `group_buy_end_at`은 `TIMESTAMP`이며, 코드에서는 KST 현지 시각으로 취급한다.

기존 흐름은 다음과 같았다.

1. 판정 스케줄러는 `LocalDateTime.now(Asia/Seoul)`로 Redis 예약을 조회한다.
2. 판정 서비스는 시간대를 지정하지 않은 `LocalDateTime.now()`로 DB를 조회한다.
3. 운영 JVM이 UTC라면, 아직 KST 마감시각에 도달하지 않은 값으로 비교하게 된다.
4. `OPEN + 마감 경과` 조건에 맞는 행을 찾지 못해 `GROUPBUY_NOT_FOUND`가 발생한다.
5. 스케줄러는 이를 stale 예약으로 간주하고 Redis member를 제거한다.

이때 DB 공동구매는 `OPEN`으로 남을 수 있고, 판정 성공 이후에 연결되는 결제 예약도 시작되지 않는다.

### 2.3 기존 복구 범위의 공백

기존 Outbox는 공동구매와 같은 트랜잭션에서 저장되며, Redis 등록 실패 시 `PENDING` 상태로 재시도했다. 그러나 등록 성공으로 `PUBLISHED`가 된 이후 Redis 예약이 삭제되거나 유실되면 다시 발행하는 경로는 없었다.

| 상황 | 기존 대응 |
| --- | --- |
| 최초 Redis 등록 실패 | Outbox 재시도 |
| 판정 도중 일시적인 오류 | Redis member를 남기고 다음 폴링에서 재시도 |
| 등록 완료 후 Redis member 유실 | 공동구매 판정 예약 복구 없음 |
| `GROUPBUY_NOT_FOUND`로 예약 삭제 | 공동구매 판정 예약 복구 없음 |
| 모집 완료 이후 결제 예약 누락 | 별도의 결제 복구 스케줄러가 담당 |

결제 복구는 `RECRUITMENT_COMPLETED` 공동구매의 결제대기 주문 및 결제 예약을 대상으로 한다. 판정되지 않은 `OPEN` 공동구매는 이 경로로 복구되지 않는다. 결제 복구 스케줄러는 `moongcheap.payments.queue.enabled=true`일 때 활성화된다.

## 3. 대화에서 결정한 설계

| 논의 | 최종 결정 |
| --- | --- |
| 배치 비교 시각을 UTC로 바꾸는 초기 수정 | 서버 전체 KST 정책에 맞춰 철회 |
| 서버 전체 시간 기준 | 애플리케이션 시작 시 JVM 기본 시간대를 `Asia/Seoul`로 설정 |
| 보드·수요의 마감 조회 | DB `CURRENT_TIMESTAMP`와 마감 컬럼을 직접 비교 |
| Redis 누락 건을 DB에서 찾아 즉시 판정하는 초기 복구 | 정상 예약의 처리 경로를 우회할 수 있어 철회 |
| 최종 복구 방식 | DB 후보를 찾고 `ZSCORE`로 부재 확인 후 ZSET에만 재등록 |
| 복구 시작 시점 | 정상 판정 예정시각에서 1시간 경과 후. 즉 마감 후 65분 |
| 정상 판정과 복구 실행 주기 | 같은 스케줄러에서 정상 판정 후 복구 실행. 기본 `fixedDelay` 10초 |
| DB 세션 KST 강제 설정 | 이번에는 추가하지 않고 운영 경과 확인 후 필요 시 검토 |
| Redis 시간대 변경 | ZSET score가 epoch milliseconds이므로 별도 변경 불필요 |
| 이미 판정된 공동구매 확인 | 기존 `OPEN` 조건과 비관적 잠금이 수행하므로 추가 사전 조회 없음 |
| 변경 의도 문서화 | 운영 코드 및 복구 인덱스에 주석 추가 |

## 4. 최종 수정 내용

### 4.1 애플리케이션 시간 기준

[MoongCheapBackendApplication.java](../src/main/java/com/moongcheap_backend/MoongCheapBackendApplication.java)에서 Spring 시작 전에 JVM 기본 시간대를 설정한다.

```java
TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Seoul")));
```

기존 `LocalDateTime.now()` 호출도 애플리케이션의 일반 실행 경로에서는 KST를 사용한다. 판정 서비스는 스케줄러와 동일하게 `LocalDateTime.now(ZONE_SEOUL)`을 명시한다.

Hibernate의 `jdbc.time_zone`은 이미 `Asia/Seoul`로 설정되어 있었다. JVM 기본 시간대와 DB 세션 시간대는 별개이며, 운영 DB 세션이 KST라고 보장하는 설정은 이번에 추가하지 않았다.

### 4.2 보드·수요 조기 마감 방지

보드 조회는 다음 조건을 사용한다.

```sql
WHERE status = 'GB_GATHERING'
  AND sale_end_at < CURRENT_TIMESTAMP
```

수요 만료는 다음 조건을 사용한다.

```sql
WHERE status IN ('UNASSIGNED', 'SUBSTITUTE_OFFERED')
  AND desire_end_at < CURRENT_TIMESTAMP
```

보드 조회 메서드에서 `threshold` 인자가 빠져도 시간 조건은 유지된다. `threshold`는 이후 `updated_at` 등 상태 변경시각 기록에 계속 사용한다.

`TIMESTAMPTZ`와 `CURRENT_TIMESTAMP`의 비교는 절대시각 기준이므로 DB 세션 표시 시간대가 UTC인지 KST인지에 따라 결과가 달라지지 않는다. 다만 저장된 마감값 자체와 DB 시스템 시계가 정확해야 한다. PostgreSQL `CURRENT_TIMESTAMP`는 트랜잭션 시작시각이며, 현재 청크 서비스가 별도 트랜잭션으로 실행되므로 배치 전체가 아닌 각 청크의 트랜잭션 기준시각을 사용한다.

관련 파일:

- [DemandBoardRepository.java](../src/main/java/com/moongcheap_backend/demand/infrastructure/demandBoard/DemandBoardRepository.java)
- [DemandRepository.java](../src/main/java/com/moongcheap_backend/demand/infrastructure/demand/DemandRepository.java)
- [DemandBoardCancelChunkService.java](../src/main/java/com/moongcheap_backend/demand/application/demandBoard/DemandBoardCancelChunkService.java)

### 4.3 정상 공동구매 판정

Redis Sorted Set 구조는 다음과 같다.

| 항목 | 값 |
| --- | --- |
| Key | `moongcheap:group-buy:judgment:pending` |
| Member | 공동구매 ID 문자열 |
| Score | 마감 후 5분 시각의 epoch milliseconds |

KST 현지 시각을 `Asia/Seoul`로 해석해 epoch milliseconds로 변환한다. Redis는 숫자 score를 비교하므로 Redis 서버의 표시 시간대를 변경할 필요는 없다. 공동구매 상태와 인원수는 member 내부에 들어 있지 않으며 DB에서 확인한다. 특정 ID의 예약 존재 여부는 `ZSCORE`로 조회할 수 있다.

정상 판정은 다음 순서로 진행된다.

1. 현재시각 이하 score의 member를 최대 100개 조회한다.
2. 각 ID에 대해 `OPEN + 마감 경과` 조건으로 DB 행을 비관적 잠금 조회한다.
3. 목표 인원 충족 시 `RECRUITMENT_COMPLETED`, 미달 시 `FAILED`로 변경한다.
4. 모집 완료 판정이 커밋되면 결제 예약 생성을 시도한다.
5. 처리한 Redis member를 제거한다.

판정 서비스 호출이 곧 외부 결제 승인 완료를 뜻하지는 않는다. 실제 결제 실행은 이후 결제 처리 경로가 담당한다.

### 4.4 유실된 판정 예약 복구

같은 스케줄러가 정상 판정 루프를 끝낸 뒤 복구를 실행한다. DB 조회는 Redis 결과에 의존하지 않으므로 Redis에 없는 공동구매 ID도 발견할 수 있다.

복구 후보 조건:

```text
status = OPEN
groupBuyEndAt <= 현재 KST - (정상 판정 지연 5분 + 복구 유예 60분)
```

후보를 마감시각, ID 순으로 최대 100개 조회한 뒤 각 ID의 Redis score를 확인한다.

- score가 있으면 이미 예약된 작업이므로 건드리지 않는다.
- score가 없으면 원래 예정시각인 `groupBuyEndAt + 5분`으로 재등록한다.
- 복구 함수는 직접 판정하지 않는다. 이후 정상 폴링에서 ZSET의 score 순서에 따라 처리한다.

예를 들어 마감이 18:34:59라면 정상 예정시각은 18:39:59, 복구 후보가 되는 시각은 19:39:59다. 이 시각 이후의 복구 실행에서 누락 예약을 발견하면 재등록한다. 이후 판정 시점은 큐 적체와 실행 시간에 따라 달라지며, 반드시 10초 이내라고 보장하지 않는다.

기본 주기는 이전 실행이 끝난 후 10초를 기다리는 `fixedDelay`다. 정상 판정과 복구가 서로 다른 독립 스케줄러로 실행되는 것은 아니다.

관련 파일:

- [GroupBuyJudgmentScheduler.java](../src/main/java/com/moongcheap_backend/groupbuy/application/GroupBuyJudgmentScheduler.java)
- [GroupBuyJudgmentService.java](../src/main/java/com/moongcheap_backend/groupbuy/application/GroupBuyJudgmentService.java)
- [GroupBuyJudgmentSchedule.java](../src/main/java/com/moongcheap_backend/groupbuy/infrastructure/GroupBuyJudgmentSchedule.java)
- [GroupBuyRepository.java](../src/main/java/com/moongcheap_backend/groupbuy/infrastructure/GroupBuyRepository.java)
- [V22__index_open_group_buy_judgment.sql](../src/main/resources/db/migration/V22__index_open_group_buy_judgment.sql): `OPEN` 행의 마감시각·ID 부분 인덱스

### 4.5 재판정 방지와 상태 구분

이미 판정된 공동구매는 `OPEN`이 아니므로 판정용 잠금 조회에 포함되지 않는다. 여러 워커가 동시에 같은 ID를 처리해도 트랜잭션 내 비관적 잠금과 상태 조건으로 재판정을 방지한다. 별도 `exists` 조회를 추가하지 않았다.

`PaymentsStatus`는 결제 상태이고 공동구매 판정 여부는 `GroupBuyStatus`가 나타낸다. 이번 수정에서는 `PaymentsStatus`를 변경하지 않았다.

## 5. 검증 결과와 범위

이번 작업 과정에서 확인한 결과는 다음과 같다.

| 검증 | 결과 및 범위 |
| --- | --- |
| 관련 스케줄러·서비스 단위 테스트 | 통과. KST 인자, 유실 예약 재등록, 기존 예약 보존, 직접 판정 미호출, 65분 cutoff 검증 |
| `./gradlew test -PskipContainerTests` | 최종 1시간 유예 변경 후 통과 |
| `MoongCheapBackendApplicationTests` | PostgreSQL/Redis 컨테이너 기반 컨텍스트 테스트 통과. 저장소 쿼리 초기화 및 Flyway 적용 확인 |
| `./gradlew compileJava` | 주석 추가 후 통과 |
| `git diff --check` | 통과 |

컨텍스트 테스트는 1시간 유예 상수 변경 이전의 복구 쿼리 구조를 대상으로 실행됐다. 이후 상수 변경은 단위 테스트 및 전체 비컨테이너 테스트로 검증했다. 컨텍스트 테스트 성공은 실제 운영 시간대 조합의 장애 재현이나 외부 결제 승인 성공을 검증했다는 뜻은 아니다. 보고서 작성 과정에서는 코드를 재확인했으며 테스트를 새로 실행하지 않았다.

## 6. 현재 구현의 한계와 운영 확인 사항

### 확인된 구현 한계

- Redis 전체 장애로 최초 `findDue()`가 실패하면 같은 실행의 DB 복구까지 도달하지 못한다. 현재 복구는 Redis 접근이 가능한 상태에서 누락 member를 보완한다.
- DB 복구는 매번 가장 오래된 100개를 조회하며 순회 커서가 없다. 앞선 100개가 장기간 `OPEN`으로 남으면 그 뒤의 누락 예약 발견이 지연될 수 있다.
- `ZSCORE` 확인과 재등록 `ZADD`는 별도 호출이며 원자적이지 않다. 그 사이 다른 워커가 등록한 score를 덮어쓰거나, 이미 판정된 ID가 다시 등록되는 경쟁 가능성은 남는다. 기존 DB 상태 조건은 재판정을 막지만 Redis 등록 경쟁까지 없애지는 않는다.
- `ZSCORE == null`은 확인 시점에 member가 없다는 뜻이다. 유실, 최초 미등록, 동시 제거의 원인을 구분하는 값은 아니다.
- 없는 데이터, 이미 판정된 데이터, 아직 마감되지 않은 데이터가 모두 `GROUPBUY_NOT_FOUND`로 처리되는 기존 예외 분류는 유지됐다.

### 배포 이후 확인

1. 애플리케이션의 실제 기동 경로에서 JVM 기본 시간대가 KST인지 확인한다.
2. 보드·수요가 마감 전 처리되지 않는지, 마감 후 배치에서 정상 처리되는지 확인한다.
3. 공동구매가 정상 예정시각 이후 판정되고 결제 예약으로 이어지는지 확인한다.
4. `Recovered missing group-buy judgment schedule` 로그와 이후 공동구매 상태 변경을 확인한다.
5. DB 세션 시간대 변경은 합의에 따라 보류한다. 재발 시 실제 저장값, JDBC 바인딩, DB 세션 시간대와 시스템 시계를 함께 확인한다.

이미 잘못 취소된 보드 #5와 `FAILED` 수요 #16은 이번 코드 변경으로 자동 원복되지 않는다. 기존 공동구매 #1~#3도 운영 DB에서 실제 상태·마감시각·Redis 예약을 확인해야 복구 대상 여부를 판단할 수 있다. 해당 데이터의 수동 변경이나 실제 결제 실행, 운영 배포는 이번 작업에서 수행하지 않았다.
