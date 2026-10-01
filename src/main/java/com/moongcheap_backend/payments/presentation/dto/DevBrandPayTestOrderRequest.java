package com.moongcheap_backend.payments.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DevBrandPayTestOrderRequest(
    @NotNull Long paymentMethodId,
    @Min(100) @Max(1_000_000) int amount,
    @NotBlank @Size(max = 100) String orderName
) {
}
