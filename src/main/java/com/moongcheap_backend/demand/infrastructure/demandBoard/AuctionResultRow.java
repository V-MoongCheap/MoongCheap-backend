package com.moongcheap_backend.demand.infrastructure.demandBoard;

import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import java.time.LocalDateTime;

public record AuctionResultRow(
    DemandStatus demandStatus,
    String catalogName,
    String catalogThumbnailUrl,
    Integer unitPrice,
    Integer shippingFee,
    Integer desiredPriceMin,
    Integer desiredPriceMax,
    String sellerName,
    Integer quantity,
    Integer participantCount,
    Long totalParticipantQuantity,
    LocalDateTime judgedAt,
    String awardReason
) {

}
