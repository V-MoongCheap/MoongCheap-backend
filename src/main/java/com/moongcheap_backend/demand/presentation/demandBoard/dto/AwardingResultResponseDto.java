package com.moongcheap_backend.demand.presentation.demandBoard.dto;

public record AwardingResultResponseDto(
    Status status,
    int appliedCount,
    int staleRejectedCount
) {

    public enum Status {APPLIED}
}
