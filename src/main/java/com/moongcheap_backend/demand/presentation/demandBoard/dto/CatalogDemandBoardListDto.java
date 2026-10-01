package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

public record CatalogDemandBoardListDto(
    List<DemandBoardCardDto> demandBoards,
    int size,
    boolean hasNext,
    int page
) {

    public static CatalogDemandBoardListDto of(List<DemandBoardCardDto> items, Pageable pageable) {
        boolean hasNext = items.size() > pageable.getPageSize();
        List<DemandBoardCardDto> demandBoards =
            hasNext ? items.subList(0, pageable.getPageSize()) : items;
        return new CatalogDemandBoardListDto(demandBoards, demandBoards.size(), hasNext,
            pageable.getPageNumber());
    }

    public record DemandBoardCardDto(
        Long id,
        int participantCount,
        int sellerCount,
        Integer priceMin,
        Integer priceMax,
        LocalDateTime saleEndAt,
        boolean isParticipating
    ) {}
}
