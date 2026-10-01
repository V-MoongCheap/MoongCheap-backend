package com.moongcheap_backend.product.presentation.productCatalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductCatalogCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 255) String thumbnailUrl,
    @Size(max = 500) String specSummary,
    Integer listPrice,
    String description,
    Long categoryId
) {

}
