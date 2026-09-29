# O 주문 생성 부하테스트

전용 데이터 준비 → 실제 공동구매 생성 → Outbox/Redis Stream/Consumer → 주문 대사를 실행한다.
`OrderService` 자체는 변경하지 않는다. 별도 낙찰 시나리오에 의존하지 않는다.

## 배포 설정

```dotenv
LOAD_TEST_ENABLED=true
PAYMENT_GATEWAY_MODE=mock
PAYMENT_WORKER_ENABLED=false
MOONGCHEAP_INTERNAL_API_KEY=<내부 API 키>
```

`PAYMENT_QUEUE_ENABLED`는 O 주문 생성에는 필수가 아니다. 기존 주문 Outbox Publisher와 Consumer는 활성 상태여야 한다.
테스트 진입점은 기본 비활성화이며 내부 API 키를 필수 검증한다. 네트워크에서도 내부 접근으로 제한한다.
서버는 mock 모드와 Worker 획득 중지 설정을 확인한 뒤에만 시딩·생성·대사를 허용한다.
QA 조작을 멈추고 테스트 시간과 데이터 정리 절차를 먼저 정한다.

## 실행

개발 PC 또는 별도 부하 발생기에 `curl`, `jq`, `k6`가 필요하다.
저장소 루트에서 실행한다. 이 명령은 지정한 배포 환경에 새 데이터를 생성한다.

```bash
export BASE_URL=https://your-test-backend
read -rs -p 'Internal API key: ' INTERNAL_API_KEY
export INTERNAL_API_KEY
export GROUP_COUNT=10
export DEMANDS_PER_GROUP=10
export LOAD_VUS=5
export VERIFY_TIMEOUT_SECONDS=300
bash tools/load-test/run-orders.sh
```

- 기본 총 주문 대상은 100건. `GROUP_COUNT=10 DEMANDS_PER_GROUP=100`이면 1,000건이다.
- 총 수요는 최대 10,000건, 공동구매는 최대 1,000개다.
- `LOAD_MAX_DURATION`은 k6 요청 구간 제한이며 기본 `5m`이다.
- `VERIFY_TIMEOUT_SECONDS`는 생성 요청 종료 후 추가 처리·대사 대기시간이다.
- tools/load-test/results/ 아래 실행별 디렉터리에 `manifest.json`, `k6-summary.json`, `verification.json`을 저장한다.
- `RESULTS_DIR`로 결과 위치를 바꿀 수 있다. Snap k6는 호스트 /tmp와 격리되므로 공유 가능한 경로를 사용한다.
- 생성 요청 실패가 있어도 대사를 시도하고, k6 실패 또는 대사 기한 초과 시 종료 코드가 0이 아니다.
- 네트워크 오류 시 자동 재시드하지 않는다. 시딩 커밋 후 응답만 유실됐을 수 있다.

현재 k6는 고정 총량·동시성 기반 burst 테스트다. 고정 RPS를 유지하는 테스트가 아니다.
[`shared-iterations`](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/shared-iterations/)로
상품 수만큼 실행하고, [`scenario.iterationInTest`](https://grafana.com/docs/k6/latest/examples/data-parameterization/)로
고유 상품을 한 번씩 배정한다. 동일 manifest를 새 부하 실행에 재사용하면 이미 생성된 공동구매를 반환하므로
신규 생성 성능 측정이 되지 않는다. 매 실행마다 새 fixture를 사용한다.

## API

모든 요청에 `X-Internal-Api-Key`가 필요하다. 응답은 이 컨트롤러의 JSON 객체다.

| 경로 | 입력 | 결과 |
|---|---|---|
| `POST /api/load-tests/internal/orders/seed` | `{"groups":10,"demandsPerGroup":10}` | 원본 수요 ID·상품 ID·기대 수량 등을 담은 manifest |
| `POST /api/load-tests/internal/orders/{runId}/products/{productId}` | 본문 없음 | 실제 생성 경로의 `groupBuyId` |
| `POST /api/load-tests/internal/orders/verify` | 시딩 시 받은 manifest 전체 | `passed`, 예상/실제 주문 수, 위반 목록 |
| `DELETE /api/load-tests/internal/orders/{runId}` | 본문 없음 | 해당 run의 DB·Stream·판정/결제 예약 삭제 건수 |

생성 진입점은 run ID로 표시된 상품만 허용하고, 같은 상품의 재전송은 기존 공동구매 ID를 반환한다.
준비된 manifest는 수정하지 않고 결과와 함께 보존한다. 대사 기준은 서버에 생성된 주문 목록이 아니라
사전에 받은 원본 manifest다. 실제 연결 수요 집합도 비교한다.

## 검증 범위와 관측

- 연결 수요마다 주문 정확히 1건, 다른 공동구매로의 연결 오류 및 비연결 수요 주문 검출.
- 회원·결제수단·판매자·상품 연결, 수량·단가·배송비·총액·주문 상태 검증.
- 참여 인원과 예상 주문 수 비교. 누락과 중복을 총건수만으로 판단하지 않는다.
- 대사는 반복 읽기 트랜잭션의 일관된 DB 스냅샷에서 수행한다.
- 검증은 10초 간격이며 큰 데이터의 대사 쿼리 비용도 DB 부하에 포함된다.
- HTTP 지표는 공동구매 생성 요청 성능이다. 실제 주문 처리량은 기존 주문 커밋 지표와 DB 결과로 확인한다.
- 최종 대사는 DB 정합성을 확인한다. Stream ACK 완료와 Outbox/Redis backlog는 대시보드에서 추가 확인한다.
- 측정 전부터 Grafana/Prometheus 수집을 시작하고 시딩 시간과 요청·처리 측정 구간을 구분한다.

## 데이터 수명과 정리

자동 삭제하지 않는다. 결과를 보존한 뒤 아래 명령으로 지정 run만 정리한다.

```bash
bash tools/load-test/cleanup-orders.sh tools/load-test/results/<실행 디렉터리>/manifest.json
```

오삭제 방지를 위해 manifest의 run ID를 다시 입력해야 한다. 서버도 상품 marker로 소유권을
재확인하며, 해당 run의 DB 관계 데이터와 Stream 메시지, 판정·결제 ZSET member만 제거한다.
기존 QA 데이터나 공유 Redis 키를 일괄 삭제하지 않는다. 같은 run의 정리가 이미 끝났으면 404를 반환한다.
새 회원·판매자·카탈로그·보드·상품·결제수단·수요를 생성하며, 주문은 실제 Consumer가 생성한다.
상품 `description`에 `load-test-order:<runId>`를 기록한다. manifest에 정리 대상 ID가 남는다.
시딩 응답 유실 시 이 marker로 최근 실행 데이터를 확인하고 무작정 재실행하지 않는다.

판매 종료시각은 준비 시점에서 7일 뒤로 두어 O 측정 중 판정이 실행되지 않게 한다.
그 시각 이후에는 실제 판정이 시작될 수 있으므로 결과 보존 후 반드시 전용 데이터를 정리해야 한다.
정리 시 실행 중 Consumer·Publisher와 예약의 상태를 확인하고 FK 의존 순서대로 해당 run만 처리한다.
QA의 실제 Toss 모드로 복귀하기 전 테스트 예약·Outbox·결제 실행 가능 상태가 남지 않았는지 확인한다.
이 fixture에는 실제 PG 결제수단이나 고객키를 등록하지 않는다. P 결제 입력으로 그대로 사용할 수 없다.

## 로컬 검증

```bash
./gradlew test --tests '*OrderLoadTestIntegrationTest'
```

Docker의 PostgreSQL/Redis를 이용해 실제 생성·발행·소비 경로, 동일 이벤트 재전달,
금액 오류·주문 누락 탐지, 다른 run 입력 거절, 내부 API 인증을 확인한다.
AWS 대상 실행은 위 도구를 배포한 뒤 별도로 수행한다.
