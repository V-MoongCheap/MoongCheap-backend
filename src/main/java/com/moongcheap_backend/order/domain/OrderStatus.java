package com.moongcheap_backend.order.domain;

public enum OrderStatus {
    PAYMENT_PENDING,      // 결제 대기
    PAYMENT_COMPLETED,    // 결제 완료
    PAYMENT_FAILED,       // 결제 실패
    PREPARING_SHIPMENT,   // 상품 준비 중
    SHIPPED,              // 배송 중
    DELIVERED,            // 배송 완료
    COMPLEDED,            // 구매 확정
    CANCELED,             // 주문 취소
    REFUND_PENDING,       // 환불 처리 중
    REFUNDED              // 환불 완료
}
