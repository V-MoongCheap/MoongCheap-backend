/* ==========================================================================
 * SEED — FN-B19-01 낙찰 결과 조회 EXPLAIN 검증용
 * --------------------------------------------------------------------------
 * 전제: sample_data.sql 이 먼저 실행되어 있어야 합니다.
 *   member 1~5, seller 1~2, product_catalog 1~5
 *   demand_board 31~38 (GB_ACTION_REQUIRED), product 34~41 (BIDDING)
 *
 * 이 파일에서 추가/변경하는 것:
 *   1. product 34 → AWARDED (demand_board 31 기준)
 *   2. product_award_evaluation for product 34
 *   3. demand rows (member 1 포함 여러 구매자 → total_participant_quantity 확인)
 *
 * EXPLAIN 시 사용할 값: demandBoardId=31, memberId=1
 * ========================================================================== */

BEGIN;

/* -------------------------------------------------- 1. product 34 → AWARDED
   demand_board 31 (GB_ACTION_REQUIRED, catalog 1) 의 낙찰 상품
*/
UPDATE "product"
SET status = 'AWARDED', awarded_at = now()
WHERE id = 34;

/* -------------------------------------------------- 2. product_award_evaluation */
INSERT INTO "product_award_evaluation" (id, product_id, demand_board_id, score, reason, is_awarded, judged_at)
VALUES
    (1, 34, 31, 0.9312, '가격 경쟁력 및 배송 일정이 수요자 희망 조건에 가장 근접', TRUE, now() - interval '2 hours');

/* -------------------------------------------------- 3. demand
   demand_board 31 에 참여한 구매자들
   member 1 → ASSIGNED  (EXPLAIN 대상)
   member 2 → ASSIGNED
   member 3 → PAYMENT_PENDING
*/
INSERT INTO "demand" (id, demand_board_id, member_id, catalog_id,
                      desired_price_min, desired_price_max,
                      desire_end_at, quantity, is_substitutable, status,
                      created_at, updated_at)
VALUES
    (101, 31, 1, 1, 3000000, 3800000, now() + interval '2 days', 2, FALSE, 'ASSIGNED',        now() - interval '5 days', now()),
    (102, 31, 2, 1, 3100000, 3900000, now() + interval '2 days', 1, FALSE, 'ASSIGNED',        now() - interval '4 days', now()),
    (103, 31, 3, 1, 2900000, 3700000, now() + interval '2 days', 3, TRUE,  'PAYMENT_PENDING', now() - interval '3 days', now());

/* sequence 동기화 */
SELECT setval(pg_get_serial_sequence('product_award_evaluation', 'id'), 100);
SELECT setval(pg_get_serial_sequence('demand',                   'id'), 200);

COMMIT;

/* ==========================================================================
 * EXPLAIN 실행용 쿼리 (값 채워진 버전)
 * ==========================================================================
EXPLAIN ANALYZE
SELECT
    d.status,
    d.quantity,
    db.participant_count,
    db.judged_at,
    pc.name             AS catalog_name,
    awarded.thumbnail_url AS catalog_thumbnail_url,
    awarded.unit_price,
    awarded.shipping_fee,
    awarded.seller_name,
    awarded.award_reason,
    (
        SELECT SUM(d2.quantity)
        FROM demand d2
        WHERE d2.demand_board_id = db.id
          AND d2.status IN ('ASSIGNED', 'PAYMENT_PENDING', 'CLOSED')
    ) AS total_participant_quantity
FROM demand d
INNER JOIN demand_board db ON d.demand_board_id = db.id
                          AND db.status = 'GB_ACTION_REQUIRED'
INNER JOIN product_catalog pc ON d.catalog_id = pc.id
LEFT  JOIN LATERAL (
    SELECT p.seller_id,
           p.thumbnail_url,
           p.unit_price,
           p.shipping_fee,
           pae.reason AS award_reason,
           s.business_name AS seller_name
    FROM product p
    LEFT  JOIN product_award_evaluation pae ON pae.product_id = p.id
    LEFT  JOIN seller s ON s.id = p.seller_id
    WHERE p.demand_board_id = db.id
      AND p.status IN ('AWARDED', 'ON_SALE')
    LIMIT 1
) awarded ON true
WHERE d.demand_board_id = 31
  AND d.member_id = 1
  AND d.status IN ('ASSIGNED', 'PAYMENT_PENDING', 'CLOSED');
 * ======================================================================== */

/* ==========================================================================
 * ROLLBACK (필요 시 수동 실행)
 * ==========================================================================
BEGIN;
DELETE FROM "demand"                   WHERE id BETWEEN 101 AND 103;
DELETE FROM "product_award_evaluation" WHERE id = 1;
UPDATE "product" SET status = 'BIDDING', awarded_at = NULL WHERE id = 34;
COMMIT;
 * ======================================================================== */
