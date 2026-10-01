package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.time.LocalDateTime;

public record DemandBoardDto(
    Long demandBoardId,
    Long catalogId,
    String thumbnailUrl,
    String catalogName,
    Integer participantCount,
    Integer sellerCount,
    Integer desiredPriceMin,
    Integer desiredPriceMax,
    LocalDateTime saleEndAt,
    boolean isParticipating
) {

    public static DemandBoardDto from(DemandBoardSummaryDto summary, boolean isParticipating) {
        return new DemandBoardDto(
            summary.demandBoardId(),
            summary.catalogId(),
            summary.thumbnailUrl(),
            summary.catalogName(),
            summary.participantCount(),
            summary.sellerCount(),
            summary.desiredPriceMin(),
            summary.desiredPriceMax(),
            summary.saleEndAt(),
            isParticipating
        );
    }
}
