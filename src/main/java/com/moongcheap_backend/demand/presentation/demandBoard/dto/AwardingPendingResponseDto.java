package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AwardingPendingResponseDto(
    String schemaVersion,
    LocalDateTime fetchedAt,
    List<Board> boards,
    int size,
    boolean hasNext
) {

    public static AwardingPendingResponseDto of(
        String schemaVersion,
        LocalDateTime fetchedAt,
        List<Board> fetched,
        int size) {
        boolean hasNext = fetched.size() > size;
        List<Board> boards = hasNext ? fetched.subList(0, size) : fetched;
        return new AwardingPendingResponseDto(
            schemaVersion, fetchedAt, boards, boards.size(), hasNext);
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
        List<Product> products,
        int maxDemandQuantityPerMember
    ) {

    }

    public record Product(
        Long productId,
        Long sellerId,
        Integer price,
        Integer quantity,
        Integer shippingFee,
        Integer minQuantity,
        Integer minParticipantCount,
        Integer maxQuantityPerMember
    ) {

    }
}
