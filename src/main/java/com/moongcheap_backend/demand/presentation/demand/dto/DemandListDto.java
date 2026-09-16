package com.moongcheap_backend.demand.presentation.demand.dto;

import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

public record DemandListDto(
    List<DemandItemDto> demands,
    int size,
    boolean hasNext,
    int page
) {

    public static DemandListDto of(List<DemandItemDto> items, Pageable pageable) {
        boolean hasNext = items.size() > pageable.getPageSize();
        List<DemandItemDto> demands = hasNext ? items.subList(0, pageable.getPageSize()) : items;
        return new DemandListDto(demands, demands.size(), hasNext, pageable.getPageNumber());
    }

    public record DemandItemDto(
        Long id,
        DemandStatus status,
        Integer desiredPriceMin,
        Integer desiredPriceMax,
        LocalDateTime desireEndAt,
        Integer quantity,
        String extraRequirement,
        boolean isSubstitutable,
        LocalDateTime createdAt,
        CatalogDto catalog,
        DemandBoardDto demandBoard
    ) {

    }

    public record CatalogDto(
        Long id,
        String name,
        String specSummary,
        String thumbnailUrl,
        Integer listPrice
    ) {

    }

    public record DemandBoardDto(
        Long id,
        int participantCount,
        Integer priceMin,
        Integer priceMax,
        LocalDateTime saleEndAt,
        CatalogDto catalog
    ) {

    }
}
