ALTER TABLE payments
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN processing_token UUID,
    ADD COLUMN processing_deadline TIMESTAMPTZ,
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN brand_pay_method_id BIGINT REFERENCES brand_pay_method(id),
    ADD COLUMN customer_key_snapshot VARCHAR(50);

UPDATE payments SET idempotency_key =
    encode(sha256(convert_to('brandpay-auto-payment-v1:' || order_no, 'UTF8')), 'hex')
WHERE payments_type = 'BRANDPAY' AND order_no IS NOT NULL;

UPDATE payments SET payments_status = CASE payments_status
    WHEN 'DONE' THEN 'SUCCEEDED'
    WHEN 'ABORTED' THEN 'FAILED'
    WHEN 'EXPIRED' THEN 'FAILED'
    WHEN 'CANCELED' THEN 'CANCELED'
    ELSE 'REVIEW_REQUIRED' END;

ALTER TABLE payments ALTER COLUMN payments_status SET DEFAULT 'PENDING';

ALTER TABLE outbox_event DROP CONSTRAINT ck_outbox_event_type;
ALTER TABLE outbox_event ADD CONSTRAINT ck_outbox_event_type CHECK (
    event_type IN ('GROUP_BUY_JUDGMENT_SCHEDULED',
                   'GROUP_BUY_ORDER_CREATION_REQUESTED',
                   'PAYMENT_SCHEDULE_SYNC'));

CREATE UNIQUE INDEX uq_payments_active_order ON payments(order_id)
    WHERE payments_status IN ('PENDING', 'UNKNOWN', 'REVIEW_REQUIRED', 'SUCCEEDED');
CREATE UNIQUE INDEX uq_payments_idempotency ON payments(idempotency_key);
ALTER TABLE payments ADD CONSTRAINT ck_payments_processing_pair
    CHECK ((processing_token IS NULL) = (processing_deadline IS NULL));
