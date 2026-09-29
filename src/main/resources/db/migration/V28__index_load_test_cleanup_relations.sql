-- FK는 PostgreSQL에서 자동으로 인덱스를 만들지 않는다.
-- 대량 주문 정리와 일반적인 부모별 조회가 전체 테이블을 반복 스캔하지 않게 한다.
CREATE INDEX IF NOT EXISTS idx_orders_group_buy_id
    ON orders(group_buy_id);

-- 기존 idx_demand_board_id_assigned는 ASSIGNED 상태만 포함하므로
-- PAYMENT_PENDING 테스트 수요의 조회/삭제에는 사용할 수 없다.
CREATE INDEX IF NOT EXISTS idx_demand_demand_board_id
    ON demand(demand_board_id);

-- 회원 삭제 전 결제수단 정리와 FK 확인을 지원한다.
CREATE INDEX IF NOT EXISTS idx_brand_pay_method_member_id
    ON brand_pay_method(member_id);
