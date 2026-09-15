package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

public record AwardingPendingResponseDto(
    String schemaVersion,
    LocalDateTime fetchedAt,
    List<Board> boards,
    int size,
    boolean hasNext,
    int page
) {

    public static AwardingPendingResponseDto of(
        String schemaVersion,
        LocalDateTime fetchedAt,
        List<Board> fetched,
        Pageable pageable) {
        boolean hasNext = fetched.size() > pageable.getPageSize();
        List<Board> boards = hasNext ? fetched.subList(0, pageable.getPageSize()) : fetched;
        return new AwardingPendingResponseDto(
            schemaVersion, fetchedAt, boards, boards.size(), hasNext, pageable.getPageNumber());
    }

    public record Board(
        Long boardId,
        Long catalogId,
        Integer priceMin,
        Integer priceMax,
        LocalDateTime saleEndAt,
        LocalDateTime calculationStartedAt,
        int participantCount,
        Long totalQuantity,
        List<Product> products
    ) {

    }

    public record Product(
        Long productId,
        Long sellerId,
        Integer price,
        Integer quantity
    ) {

    }
}
