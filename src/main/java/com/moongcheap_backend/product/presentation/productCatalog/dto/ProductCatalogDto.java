package com.moongcheap_backend.product.presentation.productCatalog.dto;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;

public record ProductCatalogDto(
    Long id,
    String name,
    String thumbnailUrl,
    Integer listPrice,
    String specSummary,
    String description
) {

    public static ProductCatalogDto from(ProductCatalog catalog) {
        return new ProductCatalogDto(
            catalog.getId(),
            catalog.getName(),
            catalog.getThumbnailUrl(),
            catalog.getListPrice(),
            catalog.getSpecSummary(),
            catalog.getDescription()
        );
    }
}
