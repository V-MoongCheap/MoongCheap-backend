<div align="center">

# 🎙 MoongCheap Showcase

### "처음 둘러보는 사람을 위한 지름길"

**선별된 핵심 문서를 10분 · 30분 · 1시간 코스로 모았습니다**

</div>

<br>

## 📌 Intro

이 폴더는 **MoongCheap 백엔드를 처음 보는 사람**이 짧은 시간에 프로젝트의 가치를 파악할 수 있도록 선별한 자료 인덱스입니다. 모든 문서의 원본은 `docs/` 또는 `tools/` 아래에 있고, 여기서는 **링크로만** 모아 두어 중복과 drift를 방지합니다.

<br>

## ⏱ 추천 코스

### ☕ 10분 코스 — 요지만

| # | 문서 | 왜 보는가 |
|:---:|:---|:---|
| 1 | **[프로젝트 개요 (README)](../README.md)** | 서비스가 뭘 하고 어떤 스택을 쓰는지 |
| 2 | **[주요 처리 흐름](../docs/flows.md)** | 수요 등록 → 공동구매 → 자동결제까지의 시퀀스 다이어그램 |
| 3 | **[설계 하이라이트](../docs/presentation/03-technical-highlights.md)** | Outbox · 3-Phase · SKIP LOCKED 등 12개 결정의 **문제 → 결정 → 효과** |

<br>

### 🥐 30분 코스 — 조금 더 깊이

| # | 문서 | 왜 보는가 |
|:---:|:---|:---|
| 4 | **[시스템 아키텍처 & 스택 근거](../docs/presentation/05-architecture-and-stack.md)** | 왜 PG + Redis + OpenSearch 조합인가 |
| 5 | **[스케줄러·워커·컨슈머 구조](../docs/presentation/02-schedulers-and-consumers.md)** | 서비스의 "자율 신경계" 9종 |
| 6 | **[데이터 흐름 (실제 클래스명 포함)](../docs/presentation/01-data-flow.md)** | 유스케이스 8종을 코드 위치와 함께 추적 |
| 7 | **[트러블슈팅 쇼케이스](../docs/presentation/08-troubleshooting-showcase.md)** | 실제 추적·해결 사례 15건 |

<br>

### 🍱 1시간 코스 — 완결

| # | 문서 | 왜 보는가 |
|:---:|:---|:---|
| 8 | **[API 규약](../docs/presentation/04-api-conventions.md)** | Swagger · ErrorCode · Security Filter |
| 9 | **[규약·산출물](../docs/presentation/06-contracts-and-deliverables.md)** | 프론트/팀/후임자 각각과의 계약 |
| 10 | **[테스트 전략](../docs/presentation/07-testing-strategy.md)** | 4층 피라미드와 부하 테스트 설계 |

<br>

## 🎯 주제별 지름길

### 🏛 "왜 이 아키텍처?"
- [시스템 아키텍처](../docs/architecture/system-architecture.md)
- [설계 하이라이트](../docs/presentation/03-technical-highlights.md)
- [아키텍처 & 스택 근거](../docs/presentation/05-architecture-and-stack.md)

### 🧯 "실제로 문제를 어떻게 추적했는지"
- [NESTED 트랜잭션 silent data loss](../docs/troubleshooting/nested-transaction-substitute-offer-analysis.md)
- [세션 RENAME 폭주 (50-스레드 401)](../docs/troubleshooting/session-rotation-race-and-awarding-path.md)
- [공동구매 판정 누락 (9시간 TZ)](../docs/troubleshooting/deadline-and-judgment-bug-report.md)
- [BrandPay 결제수단 등록 OPTIONS 501](../docs/troubleshooting/brandpay-customer-token-troubleshooting.md)
- [수요보드 리팩터 — fsync 10배 감소](../docs/troubleshooting/demand-board-refactoring-full-log.md)
- [회원 탈퇴 Outbox 전환](../docs/operations/withdraw-provider-unlink-outbox.md)

### 🧠 "설계 결정의 근거를 보고 싶다"
- [세션 기반 인증 선택 (vs JWT)](../docs/architecture/session-vs-jwt-decision.md)
- [IDENTITY → SEQUENCE 전환](../docs/architecture/identity-to-sequence-decision.md)
- [NESTED → REQUIRES_NEW 전환](../docs/architecture/nested-to-requires-new-migration-decision.md)
- [BrandPay 설계 논의 기록](../docs/brandpay/brandpay-design-history.md)
- [Redis Sorted Set 결제 큐 설계](../docs/brandpay/brandpay-redis-sorted-set-design.md)
- [상품 검색 설계 결정](../docs/domain/product-search-decisions.md)

### 🛡 "운영·신뢰성 어떻게 챙기나"
- [재시도·로깅 정책](../docs/operations/retry-and-logging.md)
- [트랜잭션/락 타임아웃 다층 방어](../docs/operations/transaction-timeout.md)
- [pod 종료 안전성 감사](../docs/operations/pod-shutdown-safety-audit.md)
- [실패 처리 감사](../docs/operations/failure-handling-audit.md)

### 🧪 "테스트 어떻게 하나"
- [테스트 전략 전체](../docs/presentation/07-testing-strategy.md)
- [동시성 테스트 시나리오](../docs/testing/concurrency-test-scenario.md)
- [부하 테스트 시나리오](../docs/load-test/scenarios.md)
- [주문·결제 부하 시나리오](../docs/load-test/order-payment-scenarios.md)

<br>

## 🖼 시각 자료

| 자료 | 설명 | 위치 |
|:---|:---|:---|
| **아키텍처 다이어그램 (PNG)** | 최신 시스템 구성 | [`docs/images/moongcheap_backend_architecture_v4.png`](../docs/images/moongcheap_backend_architecture_v4.png) |
| **아키텍처 다이어그램 (SVG)** | 발표용 벡터 | [`docs/presentation/moongcheap_backend_architecture_v3.svg`](../docs/presentation/moongcheap_backend_architecture_v3.svg) |
| **전체 ERD** | 모든 테이블 관계 | [`docs/images/MoongCheap_erd_v6.png`](../docs/images/MoongCheap_erd_v6.png) · [erdcloud 원본](https://www.erdcloud.com/d/sckTas8im4zdGZPsw) |
| **수요 → 낙찰 흐름 (SVG)** | 한 장 요약 | [`docs/images/5-2-demand-to-awarding-flow.svg`](../docs/images/5-2-demand-to-awarding-flow.svg) |
| **시퀀스 다이어그램 원본** | Mermaid `.mmd` 모음 | [`docs/images/`](../docs/images/) |

<br>

## 📎 외부 자료 (Notion)

저장소에는 **설계·구현·트러블슈팅**을, Notion에는 **기획·협의·초기 분석** 산출물을 둡니다.

| 자료 | 설명 |
|:---|:---|
| 📁 [**Mooncheap Project (전체 기술 문서)** ↗](https://app.notion.com/p/Mooncheap-Project-3f1195b2b02280bdb954ee95cb5a7f26) | PRD · 핵심 도메인 모델 · 기능 명세 수정 건의 · AI-Backend 연동 명세 · 비교 분석 보고서 등 (Notion 복제본) |
| 🧪 [**테스트 시나리오** ↗](https://app.notion.com/p/3f1195b2b022803b9ee3d9712d225f9e) | 기능 수용 기준 중심 — 단위 · 통합 · 동시성 · 부하 (Notion 복제본) |

> 전체 Notion 문서 목차와 저장소 중복 여부는 [README의 외부 자료 섹션](../README.md#-외부-자료-notion)에서 확인할 수 있습니다.

<br>

## 📑 전체 발표 자료 세트

8장짜리 발표 세트 — 10~15분 분량 리허설 가능. [`docs/presentation/`](../docs/presentation/)에 모두 있습니다.

| # | 문서 | 분량 |
|:---:|:---|:---:|
| 0 | [인덱스](../docs/presentation/00-index.md) | 1분 |
| 1 | [데이터 흐름](../docs/presentation/01-data-flow.md) | 8~10분 |
| 2 | [스케줄러·컨슈머](../docs/presentation/02-schedulers-and-consumers.md) | 5~7분 |
| 3 | [기술 하이라이트](../docs/presentation/03-technical-highlights.md) | 10~12분 |
| 4 | [API 규약](../docs/presentation/04-api-conventions.md) | 5~7분 |
| 5 | [아키텍처 & 스택 근거](../docs/presentation/05-architecture-and-stack.md) | 7~9분 |
| 6 | [규약·산출물](../docs/presentation/06-contracts-and-deliverables.md) | 4~6분 |
| 7 | [테스트 전략](../docs/presentation/07-testing-strategy.md) | 6~8분 |
| 8 | [트러블슈팅 쇼케이스](../docs/presentation/08-troubleshooting-showcase.md) | 6~8분 |

<br>

<div align="center">

**← [프로젝트 홈으로](../README.md)**

</div>
