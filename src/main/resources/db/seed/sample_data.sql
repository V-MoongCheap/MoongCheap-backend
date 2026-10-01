/* ==========================================================================
 * SAMPLE DATA — 인덱스 EXPLAIN 검증용
 * --------------------------------------------------------------------------
 * 실행 순서: member → seller → product_catalog → demand_board → product
 * 목적: demand_board 충분한 행 확보로 Index Scan vs Seq Scan 플래너 동작 확인
 * ========================================================================== */

BEGIN;

/* -------------------------------------------------- member */

INSERT INTO "member" (id, nickname, email, is_seller, terms_agreed_at, created_at, updated_at)
VALUES
    (1,  '구매자_01', 'buyer01@test.com',  FALSE, now(), now(), now()),
    (2,  '구매자_02', 'buyer02@test.com',  FALSE, now(), now(), now()),
    (3,  '구매자_03', 'buyer03@test.com',  FALSE, now(), now(), now()),
    (4,  '판매자_01', 'seller01@test.com', TRUE,  now(), now(), now()),
    (5,  '판매자_02', 'seller02@test.com', TRUE,  now(), now(), now());

/* -------------------------------------------------- seller */

INSERT INTO "seller" (id, member_id, business_name, business_number, business_number_hash,
                      mail_order_registration_number, owner_name, phone_number,
                      seller_status, approved_at, created_at, updated_at)
VALUES
    (1, 4, '테스트셀러A', '000-00-00001', 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
     '2024-서울-0001', '홍길동', '010-1111-0001', 'APPROVED', now(), now(), now()),
    (2, 5, '테스트셀러B', '000-00-00002', 'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3',
     '2024-서울-0002', '이몽룡', '010-1111-0002', 'APPROVED', now(), now(), now());

/* -------------------------------------------------- product_catalog */

INSERT INTO "product_catalog" (id, name, spec_summary, list_price, thumbnail_url, status, created_at, updated_at)
VALUES
    (1, '맥북 프로 16인치',   'M3 Pro, 18GB, 512GB', 3990000, 'https://example.com/thumb/1.jpg', 'ACTIVE', now(), now()),
    (2, '삼성 갤럭시 S25',    'Snapdragon 8 Gen 4, 12GB', 1350000, 'https://example.com/thumb/2.jpg', 'ACTIVE', now(), now()),
    (3, '소니 WH-1000XM6',   '노이즈캔슬링 헤드폰', 459000, 'https://example.com/thumb/3.jpg', 'ACTIVE', now(), now()),
    (4, '다이슨 에어랩',      '멀티스타일러 롱', 749000, 'https://example.com/thumb/4.jpg', 'ACTIVE', now(), now()),
    (5, 'LG 그램 17',        'i7-1360P, 16GB, 512GB', 2190000, 'https://example.com/thumb/5.jpg', 'ACTIVE', now(), now());

/* -------------------------------------------------- demand_board
   - GB_GATHERING      : 30건  (주요 조회 대상)
   - GB_ACTION_REQUIRED:  8건
   - GB_CLOSED         :  7건
   - GB_CANCELED       :  5건
   총 50건 → Index Scan 유도
*/

INSERT INTO "demand_board" (id, catalog_id, participant_count, price_min, price_max, status, sale_end_at, created_at, updated_at)
VALUES
    -- GB_GATHERING (30건)
    ( 1, 1,  45, 3200000, 3800000, 'GB_GATHERING', now() + interval '30 days', now(), now()),
    ( 2, 2,  12, 1100000, 1300000, 'GB_GATHERING', now() + interval '29 days', now(), now()),
    ( 3, 3,  88, 380000,  440000,  'GB_GATHERING', now() + interval '28 days', now(), now()),
    ( 4, 4,   7, 650000,  720000,  'GB_GATHERING', now() + interval '27 days', now(), now()),
    ( 5, 5,  33, 1900000, 2100000, 'GB_GATHERING', now() + interval '26 days', now(), now()),
    ( 6, 1,  21, 3100000, 3700000, 'GB_GATHERING', now() + interval '25 days', now(), now()),
    ( 7, 2,  56, 1050000, 1280000, 'GB_GATHERING', now() + interval '24 days', now(), now()),
    ( 8, 3,  14, 370000,  430000,  'GB_GATHERING', now() + interval '23 days', now(), now()),
    ( 9, 4,  99, 600000,  700000,  'GB_GATHERING', now() + interval '22 days', now(), now()),
    (10, 5,   3, 1850000, 2050000, 'GB_GATHERING', now() + interval '21 days', now(), now()),
    (11, 1,  67, 3000000, 3600000, 'GB_GATHERING', now() + interval '20 days', now(), now()),
    (12, 2,  28, 1000000, 1250000, 'GB_GATHERING', now() + interval '19 days', now(), now()),
    (13, 3,  41, 360000,  420000,  'GB_GATHERING', now() + interval '18 days', now(), now()),
    (14, 4,  19, 580000,  680000,  'GB_GATHERING', now() + interval '17 days', now(), now()),
    (15, 5,  73, 1800000, 2000000, 'GB_GATHERING', now() + interval '16 days', now(), now()),
    (16, 1,  11, 3100000, 3750000, 'GB_GATHERING', now() + interval '15 days', now(), now()),
    (17, 2,  84, 1020000, 1270000, 'GB_GATHERING', now() + interval '14 days', now(), now()),
    (18, 3,  37, 350000,  410000,  'GB_GATHERING', now() + interval '13 days', now(), now()),
    (19, 4,   5, 560000,  660000,  'GB_GATHERING', now() + interval '12 days', now(), now()),
    (20, 5,  62, 1750000, 1980000, 'GB_GATHERING', now() + interval '11 days', now(), now()),
    (21, 1,  48, 3050000, 3700000, 'GB_GATHERING', now() + interval '10 days', now(), now()),
    (22, 2,   9, 990000,  1230000, 'GB_GATHERING', now() + interval  '9 days', now(), now()),
    (23, 3,  76, 340000,  400000,  'GB_GATHERING', now() + interval  '8 days', now(), now()),
    (24, 4,  23, 540000,  640000,  'GB_GATHERING', now() + interval  '7 days', now(), now()),
    (25, 5,  51, 1700000, 1950000, 'GB_GATHERING', now() + interval  '6 days', now(), now()),
    (26, 1,  34, 3000000, 3650000, 'GB_GATHERING', now() + interval  '5 days', now(), now()),
    (27, 2,  17, 970000,  1210000, 'GB_GATHERING', now() + interval  '4 days', now(), now()),
    (28, 3,  92, 330000,  390000,  'GB_GATHERING', now() + interval  '3 days', now(), now()),
    (29, 4,   6, 520000,  620000,  'GB_GATHERING', now() + interval  '2 days', now(), now()),
    (30, 5,  44, 1650000, 1900000, 'GB_GATHERING', now() + interval  '1 days', now(), now()),

    -- GB_ACTION_REQUIRED (8건)
    (31, 1,  55, 3300000, 3900000, 'GB_ACTION_REQUIRED', now() + interval '20 days', now(), now()),
    (32, 2,  30, 1100000, 1320000, 'GB_ACTION_REQUIRED', now() + interval '18 days', now(), now()),
    (33, 3,  71, 400000,  450000,  'GB_ACTION_REQUIRED', now() + interval '15 days', now(), now()),
    (34, 4,  16, 670000,  730000,  'GB_ACTION_REQUIRED', now() + interval '12 days', now(), now()),
    (35, 5,  43, 1950000, 2150000, 'GB_ACTION_REQUIRED', now() + interval  '9 days', now(), now()),
    (36, 1,  28, 3150000, 3750000, 'GB_ACTION_REQUIRED', now() + interval  '6 days', now(), now()),
    (37, 2,  63, 1070000, 1290000, 'GB_ACTION_REQUIRED', now() + interval  '3 days', now(), now()),
    (38, 3,  10, 390000,  440000,  'GB_ACTION_REQUIRED', now() + interval  '1 days', now(), now()),

    -- GB_CLOSED (7건)
    (39, 1, 120, 3500000, 3990000, 'GB_CLOSED', now() - interval  '1 days', now(), now()),
    (40, 2,  85, 1200000, 1350000, 'GB_CLOSED', now() - interval  '3 days', now(), now()),
    (41, 3,  60, 420000,  460000,  'GB_CLOSED', now() - interval  '5 days', now(), now()),
    (42, 4,  38, 700000,  750000,  'GB_CLOSED', now() - interval  '7 days', now(), now()),
    (43, 5,  95, 2000000, 2200000, 'GB_CLOSED', now() - interval '10 days', now(), now()),
    (44, 1,  47, 3250000, 3800000, 'GB_CLOSED', now() - interval '14 days', now(), now()),
    (45, 2,  22, 1150000, 1310000, 'GB_CLOSED', now() - interval '20 days', now(), now()),

    -- GB_CANCELED (5건)
    (46, 3,   2, 350000,  400000,  'GB_CANCELED', now() - interval  '2 days', now(), now()),
    (47, 4,   1, 600000,  680000,  'GB_CANCELED', now() - interval  '6 days', now(), now()),
    (48, 5,   0, 1700000, 1900000, 'GB_CANCELED', now() - interval '11 days', now(), now()),
    (49, 1,   4, 3000000, 3600000, 'GB_CANCELED', now() - interval '18 days', now(), now()),
    (50, 2,   3, 950000,  1200000, 'GB_CANCELED', now() - interval '25 days', now(), now());

/* -------------------------------------------------- product
   - demand_board_id 1~38 (GB_GATHERING, GB_ACTION_REQUIRED) 에 BIDDING 상품 배치
   - demand_board_id 39~45 (GB_CLOSED) 에는 CLOSED 상품
   - demand_board_id 46~50 (GB_CANCELED) 는 상품 없음
*/

INSERT INTO "product" (id, catalog_id, demand_board_id, seller_id, thumbnail_url,
                        unit_price, shipping_fee, delivery_date, sale_end_at,
                        total_quantity, min_participant_count, min_quantity,
                        max_quantity_per_member, return_policy, status,
                        created_at, updated_at)
VALUES
    -- demand_board 1 (GB_GATHERING, catalog 1)
    ( 1, 1,  1, 1, 'https://example.com/p/1.jpg',  3500000, 0,    '2026-11-01', now() + interval '30 days', 100, 10, 1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    ( 2, 1,  1, 2, 'https://example.com/p/2.jpg',  3600000, 3000, '2026-11-03', now() + interval '30 days', 50,  5,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 2 (GB_GATHERING, catalog 2)
    ( 3, 2,  2, 1, 'https://example.com/p/3.jpg',  1200000, 0,    '2026-11-02', now() + interval '29 days', 80,  8,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 3 (GB_GATHERING, catalog 3)
    ( 4, 3,  3, 2, 'https://example.com/p/4.jpg',  400000,  2500, '2026-11-05', now() + interval '28 days', 200, 20, 1, 10, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    ( 5, 3,  3, 1, 'https://example.com/p/5.jpg',  410000,  0,    '2026-11-07', now() + interval '28 days', 150, 15, 1, 8,  '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 4 ~ 10 (GB_GATHERING) — 각 1건씩
    ( 7, 4,  4, 1, 'https://example.com/p/7.jpg',  680000,  0,    '2026-11-10', now() + interval '27 days', 60,  6,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    ( 8, 5,  5, 2, 'https://example.com/p/8.jpg',  1980000, 0,    '2026-11-11', now() + interval '26 days', 40,  4,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    ( 9, 1,  6, 1, 'https://example.com/p/9.jpg',  3550000, 5000, '2026-11-12', now() + interval '25 days', 70,  7,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (10, 2,  7, 2, 'https://example.com/p/10.jpg', 1150000, 0,    '2026-11-13', now() + interval '24 days', 90,  9,  1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (11, 3,  8, 1, 'https://example.com/p/11.jpg', 390000,  2500, '2026-11-14', now() + interval '23 days', 120, 12, 1, 6, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (12, 4,  9, 2, 'https://example.com/p/12.jpg', 650000,  0,    '2026-11-15', now() + interval '22 days', 55,  5,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (13, 5, 10, 1, 'https://example.com/p/13.jpg', 1900000, 0,    '2026-11-16', now() + interval '21 days', 45,  4,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 11 ~ 20 (GB_GATHERING) — 각 1건씩
    (14, 1, 11, 2, 'https://example.com/p/14.jpg', 3400000, 0,    '2026-11-17', now() + interval '20 days', 80,  8,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (15, 2, 12, 1, 'https://example.com/p/15.jpg', 1100000, 3000, '2026-11-18', now() + interval '19 days', 95,  9,  1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (16, 3, 13, 2, 'https://example.com/p/16.jpg', 380000,  0,    '2026-11-19', now() + interval '18 days', 110, 11, 1, 6, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (17, 4, 14, 1, 'https://example.com/p/17.jpg', 620000,  2500, '2026-11-20', now() + interval '17 days', 65,  6,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (18, 5, 15, 2, 'https://example.com/p/18.jpg', 1870000, 0,    '2026-11-21', now() + interval '16 days', 50,  5,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (19, 1, 16, 1, 'https://example.com/p/19.jpg', 3450000, 0,    '2026-11-22', now() + interval '15 days', 75,  7,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (20, 2, 17, 2, 'https://example.com/p/20.jpg', 1120000, 0,    '2026-11-23', now() + interval '14 days', 85,  8,  1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (21, 3, 18, 1, 'https://example.com/p/21.jpg', 370000,  3000, '2026-11-24', now() + interval '13 days', 130, 13, 1, 7, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (22, 4, 19, 2, 'https://example.com/p/22.jpg', 600000,  0,    '2026-11-25', now() + interval '12 days', 60,  6,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (23, 5, 20, 1, 'https://example.com/p/23.jpg', 1820000, 0,    '2026-11-26', now() + interval '11 days', 48,  4,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 21 ~ 30 (GB_GATHERING) — 각 1건씩
    (24, 1, 21, 2, 'https://example.com/p/24.jpg', 3480000, 5000, '2026-11-27', now() + interval '10 days', 70,  7,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (25, 2, 22, 1, 'https://example.com/p/25.jpg', 1080000, 0,    '2026-11-28', now() + interval  '9 days', 88,  8,  1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (26, 3, 23, 2, 'https://example.com/p/26.jpg', 360000,  2500, '2026-11-29', now() + interval  '8 days', 140, 14, 1, 8, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (27, 4, 24, 1, 'https://example.com/p/27.jpg', 580000,  0,    '2026-11-30', now() + interval  '7 days', 62,  6,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (28, 5, 25, 2, 'https://example.com/p/28.jpg', 1780000, 0,    '2026-12-01', now() + interval  '6 days', 52,  5,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (29, 1, 26, 1, 'https://example.com/p/29.jpg', 3420000, 0,    '2026-12-02', now() + interval  '5 days', 77,  7,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (30, 2, 27, 2, 'https://example.com/p/30.jpg', 1040000, 3000, '2026-12-03', now() + interval  '4 days', 92,  9,  1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (31, 3, 28, 1, 'https://example.com/p/31.jpg', 350000,  0,    '2026-12-04', now() + interval  '3 days', 160, 16, 1, 9, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (32, 4, 29, 2, 'https://example.com/p/32.jpg', 560000,  0,    '2026-12-05', now() + interval  '2 days', 58,  5,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (33, 5, 30, 1, 'https://example.com/p/33.jpg', 1750000, 5000, '2026-12-06', now() + interval  '1 days', 46,  4,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 31 ~ 38 (GB_ACTION_REQUIRED) — 각 1건씩
    (34, 1, 31, 2, 'https://example.com/p/34.jpg', 3700000, 0,    '2026-11-01', now() + interval '20 days', 100, 10, 1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (35, 2, 32, 1, 'https://example.com/p/35.jpg', 1250000, 0,    '2026-11-03', now() + interval '18 days', 80,  8,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (36, 3, 33, 2, 'https://example.com/p/36.jpg', 430000,  2500, '2026-11-05', now() + interval '15 days', 120, 12, 1, 6, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (37, 4, 34, 1, 'https://example.com/p/37.jpg', 710000,  0,    '2026-11-08', now() + interval '12 days', 70,  7,  1, 3, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (38, 5, 35, 2, 'https://example.com/p/38.jpg', 2100000, 0,    '2026-11-11', now() + interval  '9 days', 55,  5,  1, 2, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (39, 1, 36, 1, 'https://example.com/p/39.jpg', 3650000, 5000, '2026-11-14', now() + interval  '6 days', 85,  8,  1, 4, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (40, 2, 37, 2, 'https://example.com/p/40.jpg', 1220000, 0,    '2026-11-17', now() + interval  '3 days', 95,  9,  1, 5, '7일 이내 반품 가능', 'BIDDING', now(), now()),
    (41, 3, 38, 1, 'https://example.com/p/41.jpg', 420000,  3000, '2026-11-20', now() + interval  '1 days', 110, 11, 1, 6, '7일 이내 반품 가능', 'BIDDING', now(), now()),

    -- demand_board 39 ~ 45 (GB_CLOSED) — AWARDED 상품
    (42, 1, 39, 2, 'https://example.com/p/42.jpg', 3800000, 0,    '2026-10-01', now() - interval '1 days',  100, 10, 1, 5, '7일 이내 반품 가능', 'AWARDED', now(), now()),
    (43, 2, 40, 1, 'https://example.com/p/43.jpg', 1300000, 0,    '2026-10-03', now() - interval '3 days',  80,  8,  1, 4, '7일 이내 반품 가능', 'AWARDED', now(), now()),
    (44, 3, 41, 2, 'https://example.com/p/44.jpg', 440000,  2500, '2026-10-05', now() - interval '5 days',  120, 12, 1, 6, '7일 이내 반품 가능', 'AWARDED', now(), now()),
    (45, 4, 42, 1, 'https://example.com/p/45.jpg', 730000,  0,    '2026-10-08', now() - interval '7 days',  70,  7,  1, 3, '7일 이내 반품 가능', 'AWARDED', now(), now()),
    (46, 5, 43, 2, 'https://example.com/p/46.jpg', 2150000, 0,    '2026-10-11', now() - interval '10 days', 55,  5,  1, 2, '7일 이내 반품 가능', 'AWARDED', now(), now()),
    (47, 1, 44, 1, 'https://example.com/p/47.jpg', 3700000, 5000, '2026-10-14', now() - interval '14 days', 85,  8,  1, 4, '7일 이내 반품 가능', 'AWARDED', now(), now()),
    (48, 2, 45, 2, 'https://example.com/p/48.jpg', 1270000, 0,    '2026-10-17', now() - interval '20 days', 95,  9,  1, 5, '7일 이내 반품 가능', 'AWARDED', now(), now());

/* sequence 동기화 — identity 컬럼과 충돌 방지 */
SELECT setval(pg_get_serial_sequence('member',         'id'), 100);
SELECT setval(pg_get_serial_sequence('seller',         'id'), 100);
SELECT setval(pg_get_serial_sequence('product_catalog','id'), 100);
SELECT setval(pg_get_serial_sequence('demand_board',   'id'), 100);
SELECT setval(pg_get_serial_sequence('product',        'id'), 100);

COMMIT;


/* ==========================================================================
 * ROLLBACK (필요 시 수동 실행)
 * ==========================================================================
BEGIN;
DELETE FROM "product"         WHERE id BETWEEN 1 AND 48;
DELETE FROM "demand_board"    WHERE id BETWEEN 1 AND 50;
DELETE FROM "product_catalog" WHERE id BETWEEN 1 AND 5;
DELETE FROM "seller"          WHERE id BETWEEN 1 AND 2;
DELETE FROM "member"          WHERE id BETWEEN 1 AND 5;
COMMIT;
 * ======================================================================== */
