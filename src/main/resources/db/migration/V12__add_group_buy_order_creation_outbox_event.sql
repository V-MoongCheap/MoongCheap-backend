-- 공동구매 생성 후 주문 생성 요청을 Outbox에서 Redis Stream으로 발행할 수 있게 한다.
ALTER TABLE "outbox_event"
    DROP CONSTRAINT "ck_outbox_event_type";

ALTER TABLE "outbox_event"
    ADD CONSTRAINT "ck_outbox_event_type"
        CHECK ("event_type" IN (
            'GROUP_BUY_ORDER_CREATION_REQUESTED',
            'GROUP_BUY_JUDGMENT_SCHEDULED'
        ));
