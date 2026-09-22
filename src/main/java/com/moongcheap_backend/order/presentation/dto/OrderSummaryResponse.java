package com.moongcheap_backend.order.presentation.dto;

public record OrderSummaryResponse(
    long paymentCompleted,
    long preparingShipment,
    long shipped,
    long delivered
) {
}
