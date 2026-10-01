package com.moongcheap_backend.product.presentation.productCatalog.dto;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import java.util.List;

public record ProductCatalogSummaryListDto(
    List<ProductCatalogSummaryDto> list,
    int totalCount
) {

    public static ProductCatalogSummaryListDto from(List<ProductCatalog> catalogs) {
        List<ProductCatalogSummaryDto> list = catalogs.stream()
            .map(ProductCatalogSummaryDto::from)
            .toList();
        return new ProductCatalogSummaryListDto(
            list,
            list.size()
        );
    }

    public record ProductCatalogSummaryDto(
        Long id,
        String name,
        String thumbnailUrl,
        Integer listPrice
    ) {

        public static ProductCatalogSummaryDto from(ProductCatalog catalog) {
            return new ProductCatalogSummaryDto(
                catalog.getId(),
                catalog.getName(),
                catalog.getThumbnailUrl(),
                catalog.getListPrice()
            );
        }
    }

}
