/* ============================================================================
 * Migration: demand.pay_method_id 추가
 * ----------------------------------------------------------------------------
 * 변경 요약
 *   - demand 테이블에 pay_method_id (BIGINT, NULL 허용) 컬럼 추가
 *   - BrandpayPayMethod.id 를 참조하는 FK 추가
 *   - 기존 행은 NULL 로 유지 (결제수단 미연결 상태)
 * ========================================================================== */

BEGIN;

ALTER TABLE "demand"
    ADD COLUMN "pay_method_id" BIGINT NULL;

ALTER TABLE "demand"
    ADD CONSTRAINT "FK_brand_pay_method_TO_demand"
        FOREIGN KEY ("pay_method_id") REFERENCES "brand_pay_method" ("id");

COMMENT ON COLUMN "demand"."pay_method_id" IS '연결된 브랜드페이 결제수단 ID (brand_pay_method.id). NULL이면 결제수단 미연결 상태';

COMMIT;


/* ---------------------------------------------------------------------------
 * ROLLBACK (필요 시 수동 실행)
 * ---------------------------------------------------------------------------
BEGIN;

ALTER TABLE "demand" DROP CONSTRAINT "FK_brand_pay_method_TO_demand";
ALTER TABLE "demand" DROP COLUMN "pay_method_id";

COMMIT;
 * ------------------------------------------------------------------------- */
