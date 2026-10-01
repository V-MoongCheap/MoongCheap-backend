package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.time.LocalDateTime;

public record DemandBoardSummaryDto(
    Long demandBoardId,
    Long catalogId,
    String thumbnailUrl,
    String catalogName,
    Integer participantCount,
    Integer sellerCount,
    Integer desiredPriceMin,
    Integer desiredPriceMax,
    LocalDateTime saleEndAt
) {}
