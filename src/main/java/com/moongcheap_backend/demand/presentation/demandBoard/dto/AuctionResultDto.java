package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import com.moongcheap_backend.common.util.TimeUtils;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import java.time.LocalDateTime;

public record AuctionResultDto(
    DemandStatus demandStatus,
    String catalogName,
    String thumbnail_url,
    Integer unitPrice,
    Integer shippingFee,
    String sellerName,
    Integer quantity,
    Integer participantCount,
    Long totalParticipantQuantity,
    LocalDateTime paymentDeadlineAt,
    String awardReason
) {

    public static AuctionResultDto of(
        DemandStatus demandStatus,
        String catalogName,
        String catalogThumbnailUrl,
        Integer unitPrice,
        Integer shippingFee,
        String sellerName,
        Integer quantity,
        Integer participantCount,
        Long totalParticipantQuantity,
        LocalDateTime judgedAt,
        String awardReason
    ) {
        return new AuctionResultDto(
            demandStatus,
            catalogName,
            catalogThumbnailUrl,
            unitPrice,
            shippingFee,
            sellerName,
            quantity,
            participantCount,
            totalParticipantQuantity,
            judgedAt != null ? TimeUtils.ceilToFiveMinuteMark(judgedAt.plusHours(48)) : null,
            awardReason
        );
    }
}
