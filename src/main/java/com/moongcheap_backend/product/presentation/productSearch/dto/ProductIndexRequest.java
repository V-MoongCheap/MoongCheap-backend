package com.moongcheap_backend.product.presentation.productSearch.dto;

import jakarta.validation.constraints.NotNull;

public record ProductIndexRequest(
    @NotNull Long catalogId
) {

}
