package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DemandBoardJoinRequestDto(
    @NotNull Long payMethodId,
    @NotNull @Min(1) @Max(99) Integer quantity,
    @NotNull Boolean isSubstitutable,
    String extraRequirement,

    // DB 미저장 — 동의 여부만 검증
    @AssertTrue boolean autoPaymentAgreed,
    @AssertTrue boolean privacyCollectionAgreed,
    @AssertTrue boolean privacyThirdPartyAgreed,
    @AssertTrue boolean paymentAgencyTermsAgreed
) {

}
