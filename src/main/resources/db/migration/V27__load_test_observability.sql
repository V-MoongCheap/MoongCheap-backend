-- 기존 결제의 최초 예약 시각은 복원할 수 없으므로 NULL을 유지한다.
ALTER TABLE payments ADD COLUMN initial_scheduled_at TIMESTAMP;
ALTER TABLE outbox_event ADD COLUMN pending_since TIMESTAMP;
-- 기존 PENDING 행은 배포 시점부터 관측한다 (과거 대기시간을 추정하지 않음).
UPDATE outbox_event SET pending_since = CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul'
WHERE status = 'PENDING';
CREATE INDEX idx_outbox_pending_metrics ON outbox_event(event_type, pending_since)
WHERE status = 'PENDING';
CREATE INDEX idx_payments_backlog_metrics ON payments(payments_status, processing_deadline)
WHERE payments_status IN ('PENDING', 'UNKNOWN');

ALTER TABLE group_buy ADD COLUMN judged_at TIMESTAMP;
