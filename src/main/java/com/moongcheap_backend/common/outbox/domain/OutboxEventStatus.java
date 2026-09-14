package com.moongcheap_backend.common.outbox.domain;

public enum OutboxEventStatus {
    PENDING, // 외부 저장소 발행 대기 또는 실패 후 재시도 대기
    PUBLISHED // 외부 저장소 발행 완료
}
