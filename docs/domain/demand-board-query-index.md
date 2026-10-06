# DemandBoard 목록 조회 쿼리 & 인덱스 설계

## 1. 최종 쿼리 (`DemandBoardQueryRepositoryImpl`)

```sql
SELECT
    pc.id            AS catalog_id,
    pc.name          AS catalog_name,
    pc.thumbnail_url AS catalog_thumbnail_url,
    pc.list_price    AS catalog_list_price,
    d.id             AS board_id,
    d.participant_count,
    d.price_min      AS board_price_min,
    d.price_max      AS board_price_max,
    d.sale_end_at    AS board_sale_end_at,
    (
        SELECT COUNT(*)
        FROM product p
        WHERE p.demand_board_id = d.id
          AND p.status = 'BIDDING'
    )                AS seller_count
FROM (
    SELECT *
    FROM demand_board
    WHERE status IN (%s)
    ORDER BY sale_end_at DESC
    LIMIT :limit OFFSET :offset
) d
INNER JOIN product_catalog pc ON d.catalog_id = pc.id
```

- `seller_count`: 해당 board에 입찰 중인(`BIDDING`) 판매자 상품 수 (스칼라 서브쿼리)
- `%s`: status IN 절 — Java에서 `String.format()`으로 동적 주입
- `:limit / :offset`: NamedParameterJdbcTemplate 바인딩


## 2. 인덱스 변경 히스토리

### V7 이전 (Partial Index)

```sql
CREATE INDEX "idx_demand_board_sale_end_at_active"
    ON "demand_board" ("sale_end_at")
    WHERE "status" IN ('GB_GATHERING', 'GB_ACTION_REQUIRED');
```

**문제**: 쿼리의 IN 절이 `GB_CLOSED`, `GB_CANCELED`까지 포함할 경우 인덱스 커버리지 밖의 행이 존재하므로 플래너가 인덱스를 사용하지 않고 Seq Scan + Sort로 처리.

> Partial Index는 WHERE 조건이 쿼리의 WHERE 조건을 **완전히 포함(cover)** 해야 사용 가능하다.

### V7 이후 (Composite Index)

```sql
DROP INDEX IF EXISTS "idx_demand_board_sale_end_at_active";

CREATE INDEX "idx_demand_board_status_sale_end_at"
    ON "demand_board" ("status", "sale_end_at" DESC);
```


## 3. 인덱스 선택 기준

| 인덱스 | 적합한 케이스 |
|---|---|
| `(sale_end_at DESC)` 단독 | 전체 status 조회 + `ORDER BY sale_end_at` + LIMIT |
| `(status, sale_end_at DESC)` 복합 | 단일/소수 status 필터 + `ORDER BY sale_end_at` + LIMIT |
| Partial Index | 특정 status만 항상 조회하는 경우 (인덱스 크기 최소화) |

### 복합 인덱스로 ORDER BY가 안 되는 이유

`(status, sale_end_at DESC)` 인덱스는 내부적으로 status 기준으로 먼저 묶인 뒤 그 안에서 sale_end_at이 정렬된다.

```
(GB_ACTION_REQUIRED, 2024-12-31)
(GB_ACTION_REQUIRED, 2024-11-30)
(GB_CANCELED,        2024-12-25)
(GB_CLOSED,          2024-12-20)
(GB_GATHERING,       2024-12-28)
```

전체 status를 IN으로 조회하면 sale_end_at의 전역 정렬이 깨지므로, 4개 구간을 각각 스캔 후 merge-sort가 필요하다. 플래너는 이보다 Seq Scan + Sort가 낫다고 판단할 수 있다.


## 4. EXPLAIN에서 Quicksort가 나오는 이유

### 케이스 A — 데이터 건수 부족

```
->  Sort  (cost=11.31..11.31 rows=3 width=118)
      Sort Method: quicksort  Memory: 25kB
```

`rows=3`처럼 행이 적으면 인덱스 탐색 오버헤드보다 Seq Scan + quicksort가 더 저렴하다고 플래너가 판단. 데이터가 충분히 쌓이면 자동으로 Index Scan으로 전환된다.

### 케이스 B — Bitmap Index Scan 사용 시

```
->  Bitmap Index Scan on idx_demand_board_status_sale_end_at
->  Bitmap Heap Scan on demand_board
->  Sort (quicksort)
```

| 스캔 방식 | 정렬 보존 | Sort 필요 여부 |
|---|---|---|
| Index Scan | O | 불필요 |
| Bitmap Index Scan | X (heap 물리 순서로 fetch) | 필요 |

Bitmap Scan은 조건에 맞는 heap block 주소를 비트맵으로 수집한 뒤 일괄 fetch하므로 인덱스 순서가 깨진다. 데이터가 충분하고 LIMIT가 작으면 플래너가 Index Scan으로 전환하여 Sort를 제거한다.

### 인덱스 강제 사용 확인 방법

```sql
SET enable_seqscan = OFF;

EXPLAIN ANALYZE
SELECT * FROM demand_board
WHERE status IN ('GB_GATHERING')
ORDER BY sale_end_at DESC
LIMIT 10 OFFSET 0;

SET enable_seqscan = ON;
```


## 5. 샘플 데이터

위치: `src/main/resources/db/seed/sample_data.sql`

| 테이블 | 건수 |
|---|---|
| member | 5 |
| seller | 2 |
| product_catalog | 5 |
| demand_board | 50 (GB_GATHERING 30, GB_ACTION_REQUIRED 8, GB_CLOSED 7, GB_CANCELED 5) |
| product | 47 (BIDDING 40, AWARDED 7) |

EXPLAIN 실행 전 통계 갱신:
```sql
ANALYZE demand_board;
```
