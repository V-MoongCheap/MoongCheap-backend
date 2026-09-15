CREATE TABLE reject_history (
    demand_id       BIGINT      NOT NULL,
    demand_board_id BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT PK_REJECT_HISTORY PRIMARY KEY (demand_id, demand_board_id),
    CONSTRAINT FK_demand_TO_reject_history FOREIGN KEY (demand_id) REFERENCES demand (id) ON DELETE CASCADE,
    CONSTRAINT FK_demand_board_TO_reject_history FOREIGN KEY (demand_board_id) REFERENCES demand_board (id) ON DELETE CASCADE
);

/* ============================================================================
 * product.status 에 AWARDING, LOST 추가
 *   - AWARDING : 낙찰 판정 진행 중(AI 심사 등)
 *   - LOST     : 판정 결과 낙방
 *   - BIDDING ──심사 시작──> AWARDING ──심사 완료──> AWARDED / LOST
 * ==========================================================================*/
ALTER TABLE "product" DROP CONSTRAINT "ck_product_status";

ALTER TABLE "product" ADD CONSTRAINT "ck_product_status"
    CHECK ("status" IN ('BIDDING','AWARDING','AWARDED','LOST','ON_SALE','SOLD_OUT'));

/* ============================================================================
 * demand_board.status
 *   - V1 에서 CHECK 제약이 주석 처리되어 실제 적용되지 않음
 *   - Java enum(DemandBoardStatus)에 GB_AWARDING 추가에 따른 SQL 변경 없음
 * ==========================================================================*/

/* ============================================================================
 * uq_product_seller_board_active 갱신 — AWARDING 상태 포함
 *   - 판매자는 한 수요보드에 유효 응찰 1건만 가능하다는 제약
 *   - 판정 진행 중(AWARDING)도 활성 응찰이므로 UNIQUE 대상에 포함
 *   - LOST 는 종료 상태이므로 제외 유지
 * ==========================================================================*/
DROP INDEX IF EXISTS "uq_product_seller_board_active";

CREATE UNIQUE INDEX "uq_product_seller_board_active"
    ON "product" ("seller_id", "demand_board_id")
    WHERE "status" IN ('BIDDING', 'AWARDING', 'AWARDED', 'ON_SALE', 'SOLD_OUT');
