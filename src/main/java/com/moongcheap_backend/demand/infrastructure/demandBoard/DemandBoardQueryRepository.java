package com.moongcheap_backend.demand.infrastructure.demandBoard;

import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AuctionResultDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingPendingResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.CatalogDemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardSummaryDto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface DemandBoardQueryRepository {

    List<DemandBoardSummaryDto> getDemandBoardItems(
        List<DemandBoardStatus> statuses, Pageable pageable);

    Optional<DemandBoardSummaryDto> getDemandBoardItemsById(Long demandBoardId);

    List<CatalogDemandBoardListDto.DemandBoardCardDto> getDemandBoardsByCatalogId(
        Long catalogId, Long memberId, Pageable pageable, Integer minPrice, Integer maxPrice);

    Optional<AuctionResultDto> getAuctionResult(Long demandBoardId, Long memberId);

    List<AwardingPendingResponseDto.Board> getPendingAwardingBoards(int fetchSize);
}
