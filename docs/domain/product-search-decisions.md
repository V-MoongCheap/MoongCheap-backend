# 상품 검색 설계 결정 노트

OpenSearch 기반 상품 검색을 도입/튜닝하면서 나온 고민과 결정 사항 정리.

---

## 검색 엔진 선정

### 왜 PostgreSQL FTS만으로는 부족한가

상품 검색은 **사용자가 어떤 상품(도감)이 있는지 모르는 상태**에서 시작하는 탐색형 검색이다.
PostgreSQL의 Full Text Search는 tsvector 기반이라 **정확한 어근/단어를 알고 검색할 때** 강점이 있는 방식이라, 다음 상황에서 취약하다.

- 사용자가 "삼성 냉장고" 대신 "삼전 냉장"처럼 부분 입력/축약을 하는 경우
- 브랜드명·상품명의 오타 허용
- 자동완성 스타일 부분 매칭

→ edge_ngram으로 부분 매칭을 색인 시점에 미리 뽑아둘 수 있는 OpenSearch 채택.

### 왜 형태소 분석기(nori)를 안 썼나

상품 카탈로그는 **자연어 문장이 아니라 상품명 + 규격의 단순 나열**이다. 형태소 분석이 오히려 노이즈를 만든다.

- "무선 이어폰" 정도의 분리는 `standard` 토크나이저로도 충분
- nori는 사전 기반이라 신조어/브랜드명/외래어 상품명에 취약
- 형태소 분석 비용(색인/검색 시)이 이득 없이 발생

→ `standard` 토크나이저 + `edge_ngram`(2~5)으로 부분 매칭 커버. 초기 매핑에 있던 `description` 필드용 `product_desc_analyzer`(nori)도 함께 제거.

---

## 인덱스 설정

### refresh_interval = 5s

기본값 1초 → 5초로 상향.

- 상품 카탈로그는 실시간성 요구가 낮음 (재고/가격 실시간이 아님)
- refresh가 잦을수록 세그먼트 병합 I/O 증가 → 인덱싱 처리량 저하
- 5초 지연은 상품 검색 UX에 무해

1초는 실시간 재고/가격 등 고빈도 업데이트 케이스에 어울린다.

### description 필드 제거

- 검색 대상은 상품명 + 규격(specSummary)으로 확정
- description은 인덱싱 비용만 발생하고 검색 fields에 미포함 → 매핑에서 제거
- nori 기반 `product_desc_analyzer`도 함께 삭제

---

## 쿼리 설계

### 최종 구조

```
bool
├── filter: status = ACTIVE                (score 무관, 캐싱)
└── must:
    └── bool
        ├── should: multi_match CrossFields → name^3, specSummary^2
        │           minimum_should_match "1<75%"
        ├── should: multi_match             → name.ngram^1.5, specSummary.ngram^0.5
        └── minimum_should_match: "1"
```

### 결정 근거

- **`filter` 절에 status** — `must`가 아닌 `filter`에 두어 스코어 계산에서 제외 + 결과 캐싱
- **CrossFields로 name/specSummary 통합** — term이 여러 필드에 분산될 때 유리
- **ngram을 별도 should로 분리** — 부분 매칭이 메인 스코어를 희석시키지 않고 보너스로만 작용
- **`Operator.And`는 ngram에서 제거** — edge_ngram은 하나의 단어에서도 여러 토큰이 생성됨. And면 지나치게 엄격해짐. 기본 Or + boost로 조정
- **`minimum_should_match "1<75%"`** — 짧은 키워드(1 term)는 반드시 매치, 그 이상은 75% 매치. `"2<75%"`는 2 term 이하 키워드에 지나치게 엄격해서 완화

### 분석 파이프라인 요약

| 필드 | Analyzer | 목적 |
|---|---|---|
| `name` | standard + unit_normalizer + lowercase | 완전 단어 매칭 |
| `name.ngram` | edge_ngram(2~5) | 부분/자동완성 매칭 |
| `specSummary` | standard + unit_synonym | 규격/단위 검색 |
| `specSummary.ngram` | edge_ngram(2~5) | 규격 부분 매칭 |
| `status` | keyword | 필터링 |

- `unit_normalizer`: `ℓ/㎖/㎏` 등 유니코드 단위 문자를 표준 ASCII(`l`, `ml`, `kg`)로 정규화
- `unit_pattern_normalizer`: `500ml`처럼 붙어있는 숫자+단위를 분리해 토큰화 개선
- `unit_synonym`: `ml/밀리리터/milliliter` 같은 단위 동의어 통합

---

## 장애 대응

### Circuit Breaker + PostgreSQL 폴백

Resilience4j `@CircuitBreaker`로 OpenSearch 장애 시 PostgreSQL LIKE 검색으로 폴백.

**로깅 분리:**
- `CallNotPermittedException` → "서킷 OPEN, PostgreSQL 폴백" (원인 자명, reason 로그 불필요)
- 그 외 예외 → "OpenSearch 검색 실패, PostgreSQL 폴백" + reason 로그

운영에서 "OpenSearch가 죽었는지" vs "서킷이 열려서 차단됐는지" 구분해서 대응할 수 있게 함.

**폴백 쿼리:**
- Native SQL `LOWER(name) LIKE CONCAT('%', :name, '%')` — 대소문자 무시
- `status = 'ACTIVE'` 필터도 OpenSearch와 동일 적용
- `ORDER BY id DESC`

**폴백의 한계 (감수):**
- LIKE 검색은 relevance 스코어링 없음
- ngram/synonym/CrossFields 못 씀 → 검색 품질 저하는 불가피

---

## 데이터 저장 전략

### OpenSearch에 전체 필드 저장 (현행 유지)

DB의 상품 카탈로그 데이터 대부분을 OpenSearch 문서에도 복제.

**대안 검토:** id + name + specSummary만 저장하고 나머지는 DB에서 lookup
- 장점: DB가 유일한 source of truth, `listPrice/thumbnailUrl/status` 변경 즉시 반영
- 단점: 검색 결과마다 추가 DB 쿼리 (`WHERE id IN (...)`) 필요

**결정: 현재는 전체 저장 유지.** 대신 `status`, `listPrice`, `thumbnailUrl` 변경 시 재색인 이벤트로 sync 필요. 향후 검색 결과와 실제 표시 값이 자주 어긋나면 lookup 방식으로 전환 고려.

---

## 페이지네이션

- 응답 포맷: `{ products, size, hasNext, page }` (`DemandListDto` 패턴 재사용)
- **OpenSearch**: `from = page * size`, `size + 1` fetch → 마지막 요소로 hasNext 판별
- **PostgreSQL**: native SQL `LIMIT :limit OFFSET :offset`
- 기본 페이지 크기 20

`size + 1`을 가져와서 실제 size 초과 여부로 hasNext를 판단하는 방식. `count` 쿼리 없이 다음 페이지 존재 여부만 알아낼 수 있음.

---

## 검토했으나 채택하지 않은 것

### 매직 스트링 상수 추출

`"name^3"`, `"specSummary^2"` 같은 필드명/boost 값을 `NAME_BOOSTED` 등 상수로 추출하는 방안 검토.

**미채택 이유:**
- 각 필드가 쿼리에서 **1회씩만** 사용됨 → 오타 방지 이득이 미미
- boost 튜닝은 리터럴 한 줄 바꾸는 것과 동일한 노력
- **진짜 문제인 "JSON 매핑과 코드의 sync"는 상수화로 해결 안 됨** (매핑 JSON에서 필드명이 바뀌어도 상수는 여전히 예전 이름을 가리킴)
- 진짜 해결하려면 스키마 코드화(오버킬) 또는 통합 테스트로 실제 검색 검증이 필요

**결론:** 사용처가 여러 곳으로 늘어나거나 boost 튜닝이 잦아지면 그때 도입. 지금은 리터럴 유지.
