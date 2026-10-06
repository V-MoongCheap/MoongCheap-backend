# 상품 검색 (OpenSearch)

상품명 기반 검색을 위한 OpenSearch 연동 문서. 실제 상품 테이블은 아직 없으며, 도메인은 확장 가능한 형태로 클래스 수준에서만 정의되어 있음.

## 구성

### 패키지 구조

```
com.moongcheap_backend.product
├── domain
│   └── Product.java                    // POJO (id, name)
├── application
│   └── ProductService.java             // 색인/삭제/검색 진입점
├── infrastructure
│   ├── ProductSearchRepository.java    // OpenSearch 클라이언트 래퍼
│   └── ProductIndexInitializer.java    // 로컬 자동 생성 (조건부)
└── presentation
    ├── ProductController.java          // REST API
    └── dto
        ├── ProductIndexRequest.java
        └── ProductSearchResponse.java
```

### 리소스 및 스크립트

| 경로 | 용도 |
| --- | --- |
| `src/main/resources/opensearch/product-index.json` | 인덱스 설정/매핑 (단일 소스) |
| `scripts/opensearch/create-product-index.sh` | 운영/스테이징 최초 생성 스크립트 |

## 인덱스 설계

- **실제 인덱스명**: `product_v1`
- **별칭(alias)**: `product` (앱 코드는 항상 별칭만 참조)
- **버전 전략**: 매핑 변경 시 `product_v2`를 신규 생성 → reindex → alias 스왑 방식으로 무중단 마이그레이션

### 분석기

`name` 필드는 두 가지 방식으로 색인되어 boost 차등이 가능함.

| 서브필드 | 분석기 | 목적 |
| --- | --- | --- |
| `name` | `standard` | 단어 단위 정확 매칭 (높은 boost) |
| `name.ngram` | `product_name_ngram_analyzer` (ngram 2-3, letter/digit) | 부분 매칭 / 오타 허용 (낮은 boost) |
| `name.keyword` | `keyword` (ignore_above 256) | 정렬/집계 |

- `min_gram: 2`, `max_gram: 3` — 상품명 특성상 nori 형태소 분석은 사용하지 않음.
- multi_match boost: `name^3`, `name.ngram^1`.

## API

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/products` | 상품 문서 색인 (upsert) |
| `DELETE` | `/api/products/{id}` | 상품 문서 삭제 |
| `GET` | `/api/products/search?q=<keyword>&size=20` | 상품명 검색 |

### 요청/응답 예시

**색인**
```http
POST /api/products
Content-Type: application/json

{ "id": 1, "name": "무선 이어폰 블루투스" }
```
→ `204 No Content`

**검색**
```http
GET /api/products/search?q=이어폰&size=10
```
→ `200 OK`
```json
[
  { "id": 1, "name": "무선 이어폰 블루투스" }
]
```

## 환경별 인덱스 관리

### 로컬

`application-local.yml`의 `opensearch.auto-create-indices: true` 설정에 따라 앱 기동 시 `ProductIndexInitializer`가 자동으로 인덱스와 별칭을 생성. 기존에 존재하면 스킵.

```bash
docker compose -f docker/docker-compose.local.yml up -d opensearch
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 운영/스테이징

자동 생성은 비활성 (`opensearch.auto-create-indices` 미설정 → `@ConditionalOnProperty` 미매칭). 배포 이전에 스크립트로 인덱스와 별칭을 명시적으로 생성해야 함.

```bash
# 기본 (로컬 OpenSearch)
./scripts/opensearch/create-product-index.sh

# 운영 (basic auth)
OPENSEARCH_URL=https://opensearch.prod.internal:9200 \
OPENSEARCH_USER=admin \
OPENSEARCH_PASS='***' \
./scripts/opensearch/create-product-index.sh

# self-signed 인증서
OPENSEARCH_INSECURE=true \
OPENSEARCH_URL=https://opensearch.stage:9200 \
./scripts/opensearch/create-product-index.sh
```

특징:
- 매핑 정의는 `product-index.json`을 그대로 재사용 → 앱과 스크립트 간 스키마 드리프트 없음
- 인덱스/별칭 존재 시 스킵 → 재실행 안전 (idempotent)

### 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `OPENSEARCH_URL` | `http://localhost:9200` | OpenSearch 엔드포인트 |
| `OPENSEARCH_USER` | (없음) | 보안 플러그인 basic auth 사용자 |
| `OPENSEARCH_PASS` | (없음) | basic auth 비밀번호 |
| `OPENSEARCH_INSECURE` | (없음) | `true`일 때 TLS 인증서 검증 스킵 |

## 향후 확장 시 고려사항

- **실제 Product 테이블 도입**: `Product` POJO를 JPA `@Entity`로 승격하거나, JPA 엔티티와 검색 도큐먼트를 분리하는 방식 중 택일.
- **DB → 검색 동기화**: 트랜잭션 커밋 후 이벤트로 색인하는 outbox 패턴, 또는 배치 재색인 방식 검토 필요.
- **매핑 변경 시 마이그레이션**: `product_v2` 생성 → `_reindex` → alias 스왑 → `product_v1` 삭제 순서의 롤링 스크립트 별도 작성.
- **검색 튜닝**: ngram의 false positive가 문제되면 `search_analyzer`를 `standard`로 변경하거나, edge_ngram으로 전환 검토.
