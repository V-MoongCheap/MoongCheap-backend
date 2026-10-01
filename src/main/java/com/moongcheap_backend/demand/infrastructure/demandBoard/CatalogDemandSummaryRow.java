package com.moongcheap_backend.demand.infrastructure.demandBoard;

import java.time.LocalDateTime;

public record CatalogDemandSummaryRow(
    Long catalogId,
    Integer quickDealCount,
    String latestDemandBoardStatus,
    LocalDateTime latestSaleEndAt,
    Integer latestParticipantCount
) {

}
