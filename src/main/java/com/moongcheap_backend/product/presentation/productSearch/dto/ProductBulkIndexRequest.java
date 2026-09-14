package com.moongcheap_backend.product.presentation.productSearch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductBulkIndexRequest(
    @NotEmpty List<@NotNull Long> catalogIds
) {

}
