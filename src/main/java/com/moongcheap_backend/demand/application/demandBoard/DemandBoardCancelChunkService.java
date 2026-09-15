package com.moongcheap_backend.demand.application.demandBoard;

import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.common.lock.AdvisoryLockKeys;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemandBoardCancelChunkService {

    private final AdvisoryLockAdaptor advisoryLockAdaptor;
    private final DemandBoardRepository demandBoardRepository;
    private final ProductRepository productRepository;

    @Retry(name = "chunkRetry")
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<Integer> cancelChunk(LocalDateTime threshold, int chunkSize) {
        if (!advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH)) {
            return Optional.empty();
        }
        List<DemandBoard> chunks = demandBoardRepository.findOverdueGatheringChunk(threshold,
            chunkSize);
        if (chunks.isEmpty()) {
            return Optional.of(0);
        }

        List<Long> boardIds = chunks.stream().map(DemandBoard::getId).toList();
        Map<Long, List<Long>> productIdsByBoardId = groupProductIdsByBoardId(boardIds);

        List<Long> expiredList = new ArrayList<>();
        List<Long> boardIdsToAward = new ArrayList<>();
        List<Long> productIdsToAward = new ArrayList<>();
        for (Long boardId : boardIds) {
            List<Long> productIds = productIdsByBoardId.get(boardId);
            if (productIds == null || productIds.isEmpty()) {
                expiredList.add(boardId);
            } else {
                boardIdsToAward.add(boardId);
                productIdsToAward.addAll(productIds);
            }
        }

        if (!expiredList.isEmpty()) {
            int cancelled = demandBoardRepository.cancelBoardsAndFailDemands(expiredList,
                threshold);
            if (cancelled != expiredList.size()) {
                throw new IllegalStateException(
                    "demand_board 취소 불일치: expected=" + expiredList.size()
                        + ", actual=" + cancelled);
            }
        }
        if (!boardIdsToAward.isEmpty()) {
            int boardUpdated = demandBoardRepository.transitionStatusBulk(
                boardIdsToAward,
                DemandBoardStatus.GB_GATHERING,
                DemandBoardStatus.GB_AWARDING,
                threshold);
            if (boardUpdated != boardIdsToAward.size()) {
                throw new IllegalStateException(
                    "demand_board 상태 전이 불일치: expected=" + boardIdsToAward.size()
                        + ", actual=" + boardUpdated);
            }

            int productUpdated = productRepository.transitionStatusBulk(
                productIdsToAward,
                ProductStatus.BIDDING,
                ProductStatus.AWARDING,
                threshold);
            if (productUpdated != productIdsToAward.size()) {
                throw new IllegalStateException(
                    "product 상태 전이 불일치: expected=" + productIdsToAward.size()
                        + ", actual=" + productUpdated);
            }
        }
        return Optional.of(chunks.size());
    }

    private Map<Long, List<Long>> groupProductIdsByBoardId(List<Long> boardIds) {
        return productRepository.findProductIdsGroupedByBoardId(
            boardIds, ProductStatus.BIDDING);
    }

}
