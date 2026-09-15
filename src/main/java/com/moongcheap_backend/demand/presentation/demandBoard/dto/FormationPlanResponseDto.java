package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.util.List;

public record FormationPlanResponseDto(
    Status status,
    ExistingAssignments existingAssignments,
    List<NewBoardResult> newBoards
) {

    public enum Status { APPLIED }

    public record ExistingAssignments(int appliedCount, int staleCount) {}

    public record NewBoardResult(
        String clientBoardKey,
        Long demandBoardId,
        NewBoardStatus status
    ) {}

    public enum NewBoardStatus { CREATED, STALE_REJECTED }
}
