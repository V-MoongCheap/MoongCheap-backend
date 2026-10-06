# 수요→공동구매 파이프라인 실패 처리 감사

> 감사 대상: 수요 생성부터 공동구매 생성까지 전체 흐름
> 감사 제외: AI 판정 대기 (`GB_AWARDING` 상태 무한정 대기 허용)
> 감사 시점: 2026-09-14

---

## 감사 대상 파이프라인

| # | 단계 | 진입점 |
|---|---|---|
| 1 | 수요 생성 | `POST /api/members/me/demand` → `DemandService.create()` |
| 2 | 클러스터링/모으기 | `POST /internal/formation-plans` → `applyFormationPlan()` |
| 3 | Board Join | `POST /demand-boards/{id}/join` → `join()` |
| 4 | 마감 배치 | `DemandBoardCancelScheduler` → `cancelChunk()` |
| 5 | AI 판정 반영 | `POST /awarding/internal/result` → `applyAwardingResult()` → `award()` |
| 6 | 공동구매 생성 | `groupBuyService.createGroupBuy()` (award 내부 호출) |

---

## ✅ 정상 처리 (대부분의 경로)

| 단계 | 실패 경로 | 처리 방식 |
|---|---|---|
| **applyFormationPlan** | `StaleFormationItemException` (RuntimeException 계열) | REQUIRES_NEW 트랜잭션 자동 롤백 → 상위 catch → stale 카운트. `entityManager.detach()`는 사실상 no-op(트랜잭션 롤백으로 자동 폐기됨). 정합성 문제 없음 |
| **join** | `DEMAND_ALREADY_EXISTS`, `DEMAND_BOARD_CLOSED`, `DEMAND_BOARD_NOT_FOUND` | 각각 명시적 예외로 처리. `PESSIMISTIC_WRITE` 락으로 board 상태 재검증 불필요. `increaseParticipantCount`는 dirty checking으로 tx 내 원자적 |
| **cancelChunk** | Advisory lock 실패 | `Optional.empty()` 반환, 다음 tick 재시도. 정상 |
| **award** | 각종 CAS 실패 | `DEMAND_BOARD_NOT_FOUND` / `AWARDING_INCONSISTENT` / `NO_PARTICIPANT` 각각 stale 카운트. REQUIRES_NEW 트랜잭션이라 이 board만 롤백, 다른 board 계속 |
| **award 내 GroupBuy 생성** | `createGroupBuy` 예외 | `@Transactional`(REQUIRED)로 award tx에 편입 → 실패 시 board/demand/product 상태 모두 함께 롤백. 정합성 자동 유지 |

**공통 견고성 요소**:
- REQUIRES_NEW 격리로 iteration별 부분 실패 자연스러움
- Business 예외는 code별 catch로 stale 카운트
- `DataAccessException` catch로 board별 데이터 이상 격리 (award, substituteOffer)
- 트랜잭션 롤백으로 정합성 유지

---

## ⚠️ 잠재 리스크 (심각도 낮음~중)

### 1. 🟡 `DemandService.create` — 중복 접수 race 미처리

**위치**: `DemandService.java:59`

```java
return demandRepository.save(demand).getId();   // catch 없음
```

**시나리오**:
- 같은 회원이 같은 catalog에 대해 매우 짧은 시간에 두 번 요청
- `uq_demand_member_catalog_active` UNIQUE 제약 위반
- `DataIntegrityViolationException` → **500 응답** (사용자에겐 서버 에러로 보임)

**개선안**:
```java
try {
    return demandRepository.save(demand).getId();
} catch (DataIntegrityViolationException e) {
    throw new BusinessException(ErrorCode.DEMAND_ALREADY_EXISTS);
}
```

**우선순위**: 중 (UX 개선)

---

### 2. 🟡 `applyFormationPlan` — non-Business 예외 시 부분 성공 상태

**위치**: `DemandBoardService.java:applyFormationPlan`

**시나리오**:
- outer `@Transactional` 없음, inner `REQUIRES_NEW`
- 이미 성공한 inner iteration은 **커밋됨**
- 중간에 `DataAccessException` / `RuntimeException` 발생 → 그 지점부터 처리 중단
- 응답이 500으로 나가고, 클라이언트가 재요청 시 이미 처리된 assignment는 `updated != demandIds.size()`로 stale 처리 → 정상 흡수

**판단**: 큰 문제 아님. 각 inner가 원자적이라 데이터 정합성은 유지됨.

**개선안** (선택): `applySubstituteOfferPlan` / `applyAwardingResult`처럼 `catch (DataAccessException)` 추가하면 견고성 미세 개선.

**우선순위**: 낮

---

### 3. 🟡 `cancelChunk` — `IllegalStateException` 무한 실패 가능

**위치**: `DemandBoardCancelChunkService.cancelChunk`

**시나리오**:
- count 불일치 → `IllegalStateException` throw
- `chunkRetry`는 `TransientDataAccessException` / `DataAccessResourceFailureException`만 잡음 → **재시도 안 됨**
- 트랜잭션 롤백 → 상위 스케줄러가 다음 tick에 같은 데이터로 다시 시도 → 같은 실패 반복

**실제 발생 확률**: 극히 낮음
- 락(FOR UPDATE SKIP LOCKED)을 잡고 있는 동안 데이터가 변경돼야 발생
- 이론상 다른 프로세스가 락 우회하지 않으면 불가능

**개선안**: 발생 시 알림 (Sentry, Slack 등) — 데이터 이상 신호 조기 감지

**우선순위**: 낮 (발생 확률 낮지만 발생 시 침묵으로 반복)

---

## 🟢 오해 정정 (초기 감사에서 잘못 판단했던 것들)

트랜잭션 격리 규칙을 오해해서 문제로 지목했던 항목들. 실제로는 정상 처리됨.

| 초기 판단 | 실제 |
|---|---|
| `createNewBoard`에 orphan board 남음 | `StaleFormationItemException extends RuntimeException` → REQUIRES_NEW 롤백으로 자동 처리. `entityManager.detach()`는 no-op이지만 무해 |
| `award → createGroupBuy` 부분 성공 후 중단 | `createGroupBuy`가 `@Transactional`(REQUIRED)라 award tx에 편입. 실패 시 전체 롤백 |
| `join()` participantCount 증가 전 실패 | 단일 tx, `saveAndFlush` 후 dirty checking → tx 커밋 시 함께 반영. 부분 실패 여지 없음 |
| `GroupBuyService` try-catch 없음 → outbox 유실 | 같은 tx에 편입되어 outbox save 실패 시 GroupBuy save도 롤백. 원자적 |
| `applyFormationPlan` outer @Transactional 없음이 비정상 | **의도된 설계**. 각 inner 격리가 목적 (nested-to-requires-new-migration-decision.md 참조) |
| `saleEndAt` 재검증 없음 | `PESSIMISTIC_WRITE` 락으로 방어됨. 재검증 불필요 |

---

## 📋 개선 후보 요약 (우선순위별)

| 순위 | 항목 | 위치 | 이득 | 공수 |
|---|---|---|---|---|
| 1 | `DemandService.create`에 `DataIntegrityViolationException` catch | `DemandService.java:59` | UX 개선 (500 → 409) | 3~4줄 |
| 2 | `applyFormationPlan`에 `catch (DataAccessException)` 추가 | `DemandBoardService.java` | 견고성 미세 개선, 다른 오케스트레이터와 통일 | 10줄 이내 |
| 3 | `cancelChunk`의 `IllegalStateException` 발생 시 알림 | `DemandBoardCancelChunkService.java` | 데이터 이상 조기 감지 | 알림 인프라 의존 |

**모두 필수 아님**. 파이프라인 정합성 자체엔 영향 없음.

---

## 🎯 종합 결론

**전반적으로 실패 처리는 잘 되어 있습니다.** REQUIRES_NEW 격리 + CAS 검증 + 카운터 집계 조합이 견고하게 동작.

- **Silent data loss 없음** — 트랜잭션 롤백으로 자동 정합성 유지
- **부분 성공 위험 없음** — 각 iteration이 원자적
- **재시도 자연스러움** — stale 카운트 후 스킵, 재요청 시 정상 흡수

**필수 대응 없음**. 위 개선 후보 3개는 견고성·UX 개선용이지 파이프라인 정합성엔 영향 없음.

---

## 참고 문서

- [nested-to-requires-new-migration-decision.md](../architecture/nested-to-requires-new-migration-decision.md) — 트랜잭션 전파 방식 결정 근거
- [nested-transaction-substitute-offer-analysis.md](../troubleshooting/nested-transaction-substitute-offer-analysis.md) — 원본 NESTED 문제 분석
