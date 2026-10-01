package com.moongcheap_backend.payments.infrastructure;

import java.time.OffsetDateTime;

/** 토스에 승인된 결제의 전액 취소를 요청하는 애플리케이션 내부 규격이다. */
public interface PaymentCancellationClient {

    CancellationResponse cancel(String paymentKey, String cancelReason,
        String idempotencyKey);

    record CancellationResponse(
        String paymentKey,
        String orderId,
        String status,
        OffsetDateTime canceledAt,
        String cancelReason
    ) {
    }
}
