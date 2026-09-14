package com.moongcheap_backend.product.infrastructure.productSearch;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;

public record ProductSearchDocument(
    Long id,
    String name,
    String specSummary,
    Integer listPrice,
    String thumbnailUrl,
    String status
) {

    public static ProductSearchDocument from(ProductCatalog catalog) {
        return new ProductSearchDocument(
            catalog.getId(),
            catalog.getName(),
            catalog.getSpecSummary(),
            catalog.getListPrice(),
            catalog.getThumbnailUrl(),
            catalog.getStatus() != null ? catalog.getStatus().name() : null
        );
    }
}
