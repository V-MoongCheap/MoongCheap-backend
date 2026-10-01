package com.moongcheap_backend.demand.presentation.demand.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DemandCreateRequestDto(
    @NotNull Long catalogId,
    @NotNull Long payMethodId,
    @NotNull @Min(0) Integer desiredPriceMin,
    @NotNull @Min(0) Integer desiredPriceMax,
    @NotNull @Min(1) @Max(99) Integer quantity,
    @Size(max = 200) String extraRequirement,
    @NotNull boolean isSubstitutable,

    // DB 미저장 — 동의 여부만 검증
    @AssertTrue boolean autoPaymentAgreed,
    @AssertTrue boolean privacyCollectionAgreed,
    @AssertTrue boolean privacyThirdPartyAgreed,
    @AssertTrue boolean paymentAgencyTermsAgreed
) {

}
