/* ============================================================================
 * Migration: member.terms_agreed_at 추가
 * ----------------------------------------------------------------------------
 * 변경 요약
 *   - terms_agreed_at TIMESTAMPTZ NULL 컬럼 추가
 *   - NULL: 약관 미동의 (소셜 가입 미완료 상태)
 *   - NOT NULL: 약관 동의 완료 시각
 *
 * 기존 회원은 NULL 로 유지 (소셜 가입 완료 처리 필요 시 별도 배치)
 * ========================================================================== */

BEGIN;

ALTER TABLE "member"
    ADD COLUMN "terms_agreed_at" TIMESTAMPTZ NULL;

COMMENT ON COLUMN "member"."terms_agreed_at" IS '약관 동의 완료 시각. NULL이면 소셜 가입 미완료 상태';

COMMIT;


/* ---------------------------------------------------------------------------
 * ROLLBACK (필요 시 수동 실행)
 * ---------------------------------------------------------------------------
BEGIN;

ALTER TABLE "member" DROP COLUMN "terms_agreed_at";

COMMIT;
 * ------------------------------------------------------------------------- */


/* ============================================================================
 * demand_board 인덱스 수정
 * ----------------------------------------------------------------------------
 * 변경 요약
 *   - status 조건 'OPEN' → 'GB_GATHERING', 'GB_ACTION_REQUIRED' 로 변경
 *   - judged_at 인덱스 → sale_end_at 인덱스로 교체
 * ========================================================================== */

BEGIN;

DROP INDEX IF EXISTS "idx_demand_board_catalog_open";
DROP INDEX IF EXISTS "idx_demand_board_judged_at_open";

CREATE INDEX "idx_demand_board_catalog_active"
    ON "demand_board" ("catalog_id")
    WHERE "status" IN ('GB_GATHERING', 'GB_ACTION_REQUIRED');


CREATE INDEX "idx_demand_board_status_sale_end_at"
    ON "demand_board" ("status", "sale_end_at" DESC);

COMMIT;


/* ---------------------------------------------------------------------------
 * ROLLBACK (필요 시 수동 실행)
 * ---------------------------------------------------------------------------
BEGIN;

DROP INDEX IF EXISTS "idx_demand_board_catalog_active";
DROP INDEX IF EXISTS "idx_demand_board_status_sale_end_at";

CREATE INDEX "idx_demand_board_catalog_open"
    ON "demand_board" ("catalog_id")
    WHERE "status" = 'OPEN';

CREATE INDEX "idx_demand_board_judged_at_open"
    ON "demand_board" ("judged_at")
    WHERE "status" = 'OPEN';

COMMIT;
 * ------------------------------------------------------------------------- */


/* ============================================================================
 * demand 중복 접수 UNIQUE 인덱스 재생성
 * ----------------------------------------------------------------------------
 * 변경 요약
 *   - WHERE 절에 'PAYMENT_PENDING' 추가
 *   - 낙찰 후 결제 대기 상태에서도 동일 상품 중복 접수 차단
 * ========================================================================== */

BEGIN;

DROP INDEX IF EXISTS "uq_demand_member_catalog_active";

CREATE UNIQUE INDEX "uq_demand_member_catalog_active"
    ON "demand" ("member_id", "catalog_id")
    WHERE "status" IN ('UNASSIGNED', 'SUBSTITUTE_OFFERED', 'ASSIGNED', 'PAYMENT_PENDING');

COMMENT ON COLUMN "demand"."status" IS 'UNASSIGNED(미배정 — 클러스터 편입 대기, 최대 2일) / SUBSTITUTE_OFFERED(대체상품 제안 — 유사 상품 클러스터 편입 제안에 대한 응답 대기) / ASSIGNED(배정완료 — 수요보드 편입, 진행 중) / PAYMENT_PENDING(결제 대기) / CLOSED(종료 — 소속 보드가 GB_CLOSED 도달, 주문 생성 완료) / CANCELED(사용자 취소) / EXPIRED(소멸 — 미배정 2일 경과, 클러스터 생성 실패, 제안 무응답) / FAILED(성사 실패) / DELETED(운영자·시스템 무효화). UNASSIGNED·SUBSTITUTE_OFFERED·ASSIGNED·PAYMENT_PENDING 4종을 진행 중으로 보며 중복 접수 UNIQUE 대상이다';

COMMIT;


/* ---------------------------------------------------------------------------
 * ROLLBACK (필요 시 수동 실행)
 * ---------------------------------------------------------------------------
BEGIN;

DROP INDEX IF EXISTS "uq_demand_member_catalog_active";

CREATE UNIQUE INDEX "uq_demand_member_catalog_active"
    ON "demand" ("member_id", "catalog_id")
    WHERE "status" IN ('UNASSIGNED', 'SUBSTITUTE_OFFERED', 'ASSIGNED');

COMMIT;
 * ------------------------------------------------------------------------- */
