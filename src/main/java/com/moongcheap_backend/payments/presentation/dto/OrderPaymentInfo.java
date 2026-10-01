package com.moongcheap_backend.payments.presentation.dto;

public record OrderPaymentInfo(
    Integer productAmount,
    Integer deliveryFee,
    Integer totalPaymentAmount,
    String paymentMethod
) {

}
