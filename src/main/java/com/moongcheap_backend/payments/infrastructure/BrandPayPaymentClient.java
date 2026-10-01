package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.enums.PaymentType;
import java.time.OffsetDateTime;

/** 토스 브랜드페이 자동결제 실행 API의 애플리케이션 내부 규격이다. */
public interface BrandPayPaymentClient {

    AutomaticPaymentResponse pay(AutomaticPaymentRequest request, String idempotencyKey);

    record AutomaticPaymentRequest(
        String customerKey,
        String methodKey,
        PaymentType paymentType,
        int amount,
        String orderId,
        String orderName
    ) {
    }

    record AutomaticPaymentResponse(
        String paymentKey,
        String orderId,
        String orderName,
        int totalAmount,
        String status,
        OffsetDateTime approvedAt
    ) {
    }
}
