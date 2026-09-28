-- 결제 기록이 없는 결제대기 주문의 배송비 누락만 보정한다.
-- 이미 예약/시도/승인된 결제의 금액과 멱등성 키는 자동 변경하지 않는다.
-- 보정 중 주문 생성 및 결제 예약이 끼어들지 않도록 쓰기를 직렬화한다.
LOCK TABLE orders, payments IN SHARE ROW EXCLUSIVE MODE;

UPDATE orders o
SET total_amount = o.price * o.sum + o.delivery_fee,
    updated_at = CURRENT_TIMESTAMP
WHERE o.order_status = 'PAYMENT_PENDING'
  AND o.delivery_fee > 0
  AND o.total_amount = o.price * o.sum
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);
