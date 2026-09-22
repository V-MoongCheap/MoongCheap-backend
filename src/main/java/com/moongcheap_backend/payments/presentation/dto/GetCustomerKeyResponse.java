package com.moongcheap_backend.payments.presentation.dto;

public record GetCustomerKeyResponse(
    String clientKey,
    String customerKey
) {

}
