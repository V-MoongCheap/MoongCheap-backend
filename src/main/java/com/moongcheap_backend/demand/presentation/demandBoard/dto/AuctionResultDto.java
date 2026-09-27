package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import java.time.LocalDateTime;

public record AuctionResultDto(
    DemandStatus demandStatus,
    String catalogName,
    String thumbnail_url,
    Integer unitPrice,
    Integer shippingFee,
    Integer desiredPriceMin,
    Integer desiredPriceMax,
    Integer expectedPaymentAmount,
    String sellerName,
    Integer quantity,
    Integer participantCount,
    Long totalParticipantQuantity,
    LocalDateTime awardedAt,
    LocalDateTime paymentDeadlineAt,
    String awardReason
) {

}
