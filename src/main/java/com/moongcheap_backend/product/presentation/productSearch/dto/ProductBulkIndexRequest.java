package com.moongcheap_backend.product.presentation.productSearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProductBulkIndexRequest(
    @NotEmpty List<@NotNull Long> catalogIds
) {

}
