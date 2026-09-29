# 주문·판정·결제 관측

앱 재배포 시 Flyway V25가 최초 결제 예약 시각, 판정 시각, Outbox 대기 시작 시각 컬럼을 추가한다.
Prometheus는 기존 `/actuator/prometheus`를 15초마다 수집하면 된다. 업무 ID·회원 ID·주문번호·run ID는 메트릭 라벨로 넣지 않는다.

Grafana → Dashboards → New → Import에서 `docker/grafana/order-judgment-payment-dashboard.json`을 업로드하고 Prometheus 데이터 소스를 선택한다.
원격 Grafana에는 자동 업로드하지 않는다. dashboard의 Prometheus 및 job 변수로 데이터 소스와 앱 scrape job을 선택한다.

## 완료 처리량과 지연

`moongcheap_completed_total{stage,outcome}`는 DB 트랜잭션 **afterCommit**에서만 증가한다.

| stage | outcome | 단위 / 의미 |
|---|---|---|
| group_buy | created | 생성된 공동구매 |
| order | created | 신규 주문 행 (Stream 메시지 수 아님) |
| judgment | success / failed | 판정된 공동구매 |
| payment_preparation | scheduled / failed | 새로 준비한 결제; 기존 결제 반환은 제외 |
| payment_schedule_sync | scheduled / removed | 커밋된 Redis 예약 반영/제거; 재예약도 포함, 고유 결제 수 아님 |
| payment | succeeded / failed / review_required | 결제 결과 전환; PG 재호출과 분리 |
| payment_attempt | initial / reconciliation | 커밋된 실행권 획득; PG 호출 전 중단될 수 있음 |
| payment_retry | scheduled | 재시도 예약 |
| outbox_publish | 이벤트 종류 | 주문/판정 이벤트 발행 |
| outbox_retry | 이벤트 종류 | 발행 실패 후 재시도 예약 |

`moongcheap_completion_delay_seconds_{bucket,count,sum,max}`:

- order: 공동구매 생성 → 신규 주문 커밋. 공동구매 처리 배치당 1개 표본이며 주문별 표본이 아니다. 재전달로 신규 주문이 0개면 제외.
- judgment: 판매 종료 + 5분 → 판정 커밋.
- payment_preparation: 판정 시각 → 결제 준비 커밋 (주문별 표본).
- payment: DB 최초 예약 시각 → 결과 커밋. Redis claim/재시도에 영향을 받지 않는다.
- payment_schedule_sync / outbox_publish: 해당 Outbox의 연속 PENDING 시작 → 발행 결과 커밋. 결제 재예약 시에는 최초 예약 생성 지연과 다르다.

기존 결제의 최초 예정시각과 기존 공동구매 판정 시각은 복원하지 않고 NULL로 둔다. 해당 지연 표본은 제외한다.
기존 PENDING Outbox의 대기 시작은 마이그레이션 시각으로 초기화한다. 새 테스트 데이터로 비교한다.
앱 호스트와 DB/Redis 시각을 동기화해야 하며 벽시각은 Asia/Seoul로 해석한다.

```promql
# 초당 신규 주문 수
sum(rate(moongcheap_completed_total{stage="order",outcome="created"}[1m]))

# 최초 예약 → 결제 성공 p99 (초)
histogram_quantile(0.99, sum by(le) (
  rate(moongcheap_completion_delay_seconds_bucket{stage="payment",outcome="succeeded"}[5m])
))

# 선택한 실행 시간 범위 내 관측 최대 지연 (Grafana instant query)
max(max_over_time(moongcheap_completion_delay_seconds_max{stage="payment"}[$__range]))
```

카운터는 프로세스 로컬이며 재시작된다. `rate`/`increase`를 사용하고 테스트 시작 전 최소 두 번 scrape한다.
Prometheus 수치는 관측용이다. 커밋 직후 프로세스 종료, scrape 이전 재시작 등에서는 유실될 수 있으므로 정합성/정확한 최종 건수는 manifest와 DB·승인 이력으로 대사한다.
`*_max`는 Micrometer rolling max이며 전체 실행 최대값은 위의 범위 쿼리로 집계한다. 15초 scrape로 매우 짧은 순간 적체는 놓칠 수 있다.

## 실행 시간, PG, Worker, 기본 자원

- `moongcheap_operation_seconds_*{operation,outcome}`: 생성, 주문, 판정, 예약, 실행 트랜잭션, Worker 호출의 소요시간. AOP가 트랜잭션 외부에서 측정하며 커밋/롤백 시간을 포함한다. `returned`는 정상 반환이지 업무 성공 판정이 아니다.
- `moongcheap_pg_request_seconds_*{operation,outcome}`: `pay`/`findByOrderId` 호출 횟수·시간. outcome은 returned 또는 오류 분류. **신규 승인 수가 아니다.** 재호출/조회도 포함하며 별도의 PG 오류 코드·요청 내용은 라벨로 넣지 않는다.
- `moongcheap_worker_active`: 인스턴스에서 실행 중인 runOne 수. claim 조회부터 결과 반영까지 포함. `rate(moongcheap_operation_seconds_sum{operation="runOne"}[1m])`은 구간 평균 점유 수이다.
- HTTP 히스토그램을 활성화했으므로 `http_server_requests_seconds_bucket`으로 HTTP p95/p99를 계산할 수 있다.
- JVM CPU/heap/GC와 Hikari active/pending은 기존 기본 지표를 사용한다. Hikari 획득 시간은 SQL 실행 시간과 다르다.

## 적체 수집

기본 15초 주기, 전용 metrics 스케줄러에서 읽기 전용 집계 후 캐시한다. scrape 시 DB/Redis를 호출하지 않는다.
`METRICS_BACKLOG_ENABLED=false`로 비활성화, `METRICS_BACKLOG_INTERVAL_MS`로 조회 빈도를 조절한다. DB 집계 쿼리 timeout은 3초이다.
대용량 데이터에서는 집계 비용을 관찰하고 주기를 늘린다. 여러 인스턴스에 활성화하면 각각 조회한다.

| 지표 | 의미 |
|---|---|
| moongcheap_outbox_pending{type} | 유형별 미발행 이벤트 수 |
| moongcheap_outbox_oldest_seconds{type} | 연속 미발행 최장 대기; 재시도 대기시간을 포함 |
| moongcheap_queue_size{queue} | 판정/결제 ZSET 전체 크기 |
| moongcheap_queue_due{queue} | Redis TIME 기준 현재 도래 건수 |
| moongcheap_stream_messages{state} | 주문 consumer group의 lag / pending |
| moongcheap_payment_backlog{state} | pending / processing / retry_wait / lease_expired |
| moongcheap_postgres_connections{state} | 현재 DB의 active / lock_wait 연결 수 |
| moongcheap_postgres_oldest_transaction_seconds | 현재 DB 최장 열린 트랜잭션 경과 |
| moongcheap_collector_up{source} | 수집 성공 1, 실패/미지원 0 |
| moongcheap_collector_last_success_timestamp_seconds{source} | 마지막 성공 UNIX 시각 |
| moongcheap_collector_errors_total{source} | 수집 예외 수 |

processing은 미만료 DB lease 보유, lease_expired는 만료 lease 보유, retry_wait는 lease 없는 UNKNOWN이다. UNKNOWN은 승인 여부 미확정 상태이므로 실패로 해석하지 않는다.
ZSET 전체 크기는 순수 대기 수가 아니며 due=0도 완료를 뜻하지 않는다. consumer group 미생성, lag 미지원은 NaN 및 collector_up=0으로 표시한다.
공유 DB/Redis 지표는 인스턴스별 값을 **합산하지 않는다.** 제공한 대시보드는 정상 수집 중이며 최근 60초 안에 갱신한 값만 max 집계한다. 수집 주기를 늘리면 대시보드의 60초 freshness 기준도 늘린다.
pg_stat_activity는 해당 DB 계정이 볼 수 있는 범위만 제공한다. 다른 계정까지 보려면 운영 정책에 따른 pg_read_all_stats 권한이 필요하다.

## 앱 밖에서 추가 연결할 관측

이 저장소에는 AWS 모니터링 배포 설정과 Mock PG 구현이 없다. 다음은 앱 계측만으로 수집할 수 없으며 환경에서 연결해야 한다.

- PostgreSQL 쿼리별 지연: postgres_exporter와 pg_stat_statements 설정. 앱은 현재 active/락 대기/최장 트랜잭션만 직접 수집한다.
- DB/Redis/Mock의 CPU·메모리: 호스트 node_exporter, 컨테이너 cAdvisor 또는 AWS 관리형 서비스의 CloudWatch 연계. 앱 JVM CPU를 DB CPU로 해석하지 않는다.
- Redis 메모리·명령 지연: redis_exporter. 앱은 업무 Stream/ZSET만 직접 수집한다.
- Mock 신규 승인 수·승인 이력: Mock 서버에서 멱등키별 최초 승인만 집계하고 대사용 이력을 보존한다.
- 목표/실제 유입량·dropped iterations: k6 결과/Prometheus 연계.
- O/J/P/run별 정확한 분리, 중복·누락·잘못된 금액·상태: manifest와 DB·Mock 건별 검증. run ID를 공용 Prometheus 태그로 무제한 생성하지 않는다.
- backlog 해소 시간: 부하 발생기의 유입 종료 시각과 DB 대사로 확인한 전체 완료 시각의 차이. 큐가 잠깐 0인 시각만으로 판정하지 않는다.

이 지표와 대시보드는 정합성 검증기나 부하 발생기를 대체하지 않는다.

## 서비스 메서드별 수행시간

`moongcheap_service_execution_seconds_{bucket,count,sum,max}{service,method,outcome}`는
`com.moongcheap_backend` 아래 `@Service` 빈의 public 메서드가 Spring 프록시를 통해 호출될 때 측정한다.
서비스 태그는 패키지를 포함한 클래스명이며, 동일 클래스의 오버로드는 메서드명 기준으로 합산한다.
호출 인자·회원 ID·결제 ID·예외 메시지는 태그로 넣지 않는다.

- Grafana의 Service 선택기로 범위를 좁혀 호출/초, 평균, P95, P99, 최근 최대, 예외/초를 확인한다. P50은 제공하지 않는다.
- `returned`는 정상 반환, `error`는 예외가 호출자에게 전달된 경우이다. 내부에서 처리한 예외나 실패 결과 반환은 returned에 속한다.
- 경과시간에는 하위 서비스, DB, 외부 API 대기가 포함된다. A → B 호출이면 A에도 B 시간이 포함되므로 두 시간을 합산하지 않는다.
- 메서드가 새 트랜잭션을 시작했다면 커밋/롤백까지 포함한다. 기존 외부 트랜잭션에 참여한 호출은 외부 트랜잭션의 나중 커밋 시간을 포함하지 않는다.
- 같은 객체 안의 `this` 호출, private 메서드, 프록시를 거치지 않는 직접 생성 객체는 별도 측정하지 않는다. 향후 비동기 메서드는 호출 반환까지의 시간만 측정한다.
- 서비스 호출은 프로세스 로컬 호출 수이며 업무 완료 건수가 아니다. 기존 `moongcheap_completed_total`과 구분한다.
- 호출된 서비스/메서드부터 시계열이 생성된다. 앱 재시작 후 대상 업무를 호출하고 최소 두 번 scrape한 뒤 rate/백분위를 확인한다.
- 최댓값은 rolling max이다. 히스토그램의 유한 버킷 상한은 5분이므로 그보다 긴 작업의 백분위는 해상도가 제한된다.

```promql
# 서비스/메서드별 P99 (정상 반환 호출)
histogram_quantile(0.99, sum by(le,service,method) (
  rate(moongcheap_service_execution_seconds_bucket{outcome="returned"}[5m])
))
```
