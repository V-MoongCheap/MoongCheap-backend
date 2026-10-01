package com.moongcheap_backend.product.presentation.productSearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProductIndexRequest(
    @NotNull Long catalogId
) {

}
