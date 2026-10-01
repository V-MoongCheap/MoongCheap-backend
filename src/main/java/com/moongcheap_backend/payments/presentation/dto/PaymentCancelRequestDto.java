package com.moongcheap_backend.payments.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequestDto(
    @NotBlank
    @Size(max = 200)
    String cancelReason
) {
}
