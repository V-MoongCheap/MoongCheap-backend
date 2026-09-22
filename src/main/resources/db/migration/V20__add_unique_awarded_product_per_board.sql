-- board 하나에 낙찰 상태(AWARDED/ON_SALE/SOLD_OUT) product는 최대 1개여야 한다.
-- 기존 uq_product_seller_board_active 는 (seller_id, demand_board_id) 조합의 유일성만
-- 보장하므로, 서로 다른 판매자의 상품이 동시에 낙찰 상태가 되는 것을 막지 못한다.

CREATE UNIQUE INDEX "uq_product_board_awarded"
    ON "product" ("demand_board_id")
    WHERE "status" IN ('AWARDED', 'ON_SALE', 'SOLD_OUT');
