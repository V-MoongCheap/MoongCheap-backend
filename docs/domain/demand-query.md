# findDemandItemsByMemberId 구현 노트

## 위치
`demand/infrastructure/demand/DemandQueryRepositoryImpl`

---

## DDD 구조 결정

`Demand`는 `catalogId`, `demandBoardId`를 `Long`으로만 보관 (ID-only 참조).
cross-aggregate JOIN이 필요한 read 전용 쿼리는 `DemandRepository`가 아닌 별도 `DemandQueryRepository`로 분리.

- `DemandRepository` → Demand aggregate 조작 (write-side)
- `DemandQueryRepository` → 조회 전용, cross-aggregate JOIN 허용 (read-side)

---

## EntityManager → JdbcTemplate + RowMapper 변경 이유

| 방식 | 문제 |
|---|---|
| `EntityManager` + `Object[]` | SELECT 컬럼 순서가 바뀌면 인덱스 기반 매핑이 조용히 깨짐 |
| `JdbcTemplate` + `RowMapper` | 컬럼명 기반 접근이라 순서 변경/컬럼 추가에 안전 |

---

## IN 절 파라미터 바인딩 문제

`NamedParameterJdbcTemplate`에 `List<String>`을 `:statuses`로 바인딩���면
PostgreSQL이 `= ANY('{...}'::text[])` 로 변환하면서 `status` 컬럼에 `::text` 캐스트가 붙어 인덱스를 못 탐.

**해결**: enum `.name()` 값을 문자열로 직접 조립해 IN 절에 인라인.
enum 값이라 SQL injection 위험 없음.

```java
String inClause = statuses.stream()
    .map(s -> "'" + s.name() + "'")
    .collect(Collectors.joining(", "));
// → 'UNASSIGNED', 'SUBSTITUTE_OFFERED', 'ASSIGNED', 'PAYMENT_PENDING'
```

---

## 페이지네이션 최적화 (서브쿼리)

### 변경 전 (비효율)
```sql
FROM demand d
INNER JOIN product_catalog pc ...
LEFT  JOIN demand_board   db ...
WHERE d.member_id = 1 AND d.status IN (...)
ORDER BY d.created_at DESC
LIMIT 20 OFFSET 0
-- 전체 JOIN 후 정렬 → LIMIT
```

### 변경 후 (최적)
```sql
FROM (
    SELECT *
    FROM demand
    WHERE member_id = 1
      AND status IN (...)
    ORDER BY created_at DESC
    LIMIT 20 OFFSET 0   -- demand만 먼저 20건 확정
) d
INNER JOIN product_catalog pc ON d.catalog_id = pc.id
LEFT  JOIN demand_board   db ON d.demand_board_id = db.id
-- 20건에만 JOIN
```

---

## 인덱스

`idx_demand_member_status ON demand (member_id, status)` 사용.

ORDER BY `created_at DESC`는 별도 Sort 노드 발생 (인덱스가 `created_at` 미포함).
Sort를 없애려면 `(member_id, status, created_at DESC)` 복합 인덱스 필요 — 현재 미적용.

---

## demand_board null 처리

`demand_board_id`가 null이면 LEFT JOIN 결과 `board_id`도 null.
`board_id == null` 체크 후 `DemandBoardDto` 자체를 null로 반환 (내부 필드를 null로 채운 객체가 아님).

---

## getInt vs getObject 구분

| 메서드 | 사용 대상 | 이유 |
|---|---|---|
| `rs.getInt("participant_count")` | NOT NULL 컬럼 | null 없음, primitive 반환 |
| `rs.getObject("board_price_min", Integer.class)` | nullable 컬럼 | null → null 유지 (0으로 오염 방지) |
