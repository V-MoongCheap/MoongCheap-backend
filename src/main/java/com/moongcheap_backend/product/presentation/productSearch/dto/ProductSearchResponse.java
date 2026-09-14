package com.moongcheap_backend.product.presentation.productSearch.dto;

import com.moongcheap_backend.product.infrastructure.productSearch.ProductSearchDocument;
import java.util.List;

public record ProductSearchResponse(
    List<ProductSearchItemDto> products,
    int size,
    boolean hasNext,
    int page
) {

    public static ProductSearchResponse of(List<ProductSearchDocument> docs, int page, int pageSize) {
        boolean hasNext = docs.size() > pageSize;
        List<ProductSearchItemDto> items = (hasNext ? docs.subList(0, pageSize) : docs).stream()
            .map(ProductSearchItemDto::from)
            .toList();
        return new ProductSearchResponse(items, items.size(), hasNext, page);
    }

    public record ProductSearchItemDto(
        Long id,
        String name,
        String specSummary,
        Integer listPrice,
        String thumbnailUrl,
        String status
    ) {

        public static ProductSearchItemDto from(ProductSearchDocument doc) {
            return new ProductSearchItemDto(
                doc.id(),
                doc.name(),
                doc.specSummary(),
                doc.listPrice(),
                doc.thumbnailUrl(),
                doc.status()
            );
        }
    }
}
