-- 오래된 OPEN 공동구매만 반복 조회하는 판정 예약 복구 쿼리를 지원한다.
CREATE INDEX "idx_group_buy_open_end_at"
    ON "group_buy" ("group_buy_end_at", "id")
    WHERE "status" = 'OPEN';
