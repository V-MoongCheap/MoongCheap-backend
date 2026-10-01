package com.moongcheap_backend.demand.presentation.demandBoard.dto;

import java.util.List;

public record DemandBoardListDto(
    List<DemandBoardSummaryDto> demandBoards
) {

}
