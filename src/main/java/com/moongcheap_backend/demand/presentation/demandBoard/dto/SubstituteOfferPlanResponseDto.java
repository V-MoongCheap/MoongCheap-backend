package com.moongcheap_backend.demand.presentation.demandBoard.dto;

public record SubstituteOfferPlanResponseDto(
    Status status,
    int appliedCount,
    int alreadyAppliedCount,
    int staleRejectedCount
) {

    public enum Status { APPLIED }
}
