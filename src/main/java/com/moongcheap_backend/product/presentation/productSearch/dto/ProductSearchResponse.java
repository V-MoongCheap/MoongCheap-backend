package com.moongcheap_backend.product.presentation.productSearch.dto;

import com.moongcheap_backend.demand.infrastructure.demandBoard.CatalogDemandSummaryRow;
import com.moongcheap_backend.product.infrastructure.productSearch.ProductSearchDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProductSearchResponse(
    List<ProductSearchItemDto> products,
    int size,
    boolean hasNext,
    int page
) {

    public static ProductSearchResponse of(
        List<ProductSearchDocument> docs,
        Map<Long, CatalogDemandSummaryRow> summariesByCatalogId,
        int page,
        int pageSize
    ) {
        boolean hasNext = docs.size() > pageSize;
        List<ProductSearchItemDto> items = (hasNext ? docs.subList(0, pageSize) : docs).stream()
            .map(doc -> ProductSearchItemDto.of(doc, summariesByCatalogId.get(doc.id())))
            .toList();
        return new ProductSearchResponse(items, items.size(), hasNext, page);
    }

    public record ProductSearchItemDto(
        Long id,
        String name,
        String specSummary,
        Integer listPrice,
        String thumbnailUrl,
        String status,
        Integer quickDealCount,
        String demandBoardStatus,
        LocalDateTime saleEndAt,
        Integer participantCount
    ) {

        public static ProductSearchItemDto of(ProductSearchDocument doc, CatalogDemandSummaryRow summary) {
            return new ProductSearchItemDto(
                doc.id(),
                doc.name(),
                doc.specSummary(),
                doc.listPrice(),
                doc.thumbnailUrl(),
                doc.status(),
                summary != null ? summary.quickDealCount() : 0,
                summary != null ? summary.latestDemandBoardStatus() : null,
                summary != null ? summary.latestSaleEndAt() : null,
                summary != null ? summary.latestParticipantCount() : null
            );
        }
    }
}
