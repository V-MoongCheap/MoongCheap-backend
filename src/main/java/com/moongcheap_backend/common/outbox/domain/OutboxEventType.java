package com.moongcheap_backend.common.outbox.domain;

public enum OutboxEventType {
    GROUP_BUY_ORDER_CREATION_REQUESTED, // 공동구매 생성 직후 주문 생성을 Redis Stream에 요청
    GROUP_BUY_JUDGMENT_SCHEDULED // 공동구매 만료 판정을 Redis에 예약
}
