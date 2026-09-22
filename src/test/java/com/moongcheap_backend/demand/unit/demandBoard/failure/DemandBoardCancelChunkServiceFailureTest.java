package com.moongcheap_backend.demand.unit.demandBoard.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.common.lock.AdvisoryLockKeys;
import com.moongcheap_backend.demand.application.demandBoard.DemandBoardCancelChunkService;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemandBoardCancelChunkServiceFailureTest {

    @Mock
    private AdvisoryLockAdaptor advisoryLockAdaptor;
    @Mock
    private DemandBoardRepository demandBoardRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DemandBoardCancelChunkService service;

    private DemandBoard boardWithId(long id) {
        DemandBoard board = mock(DemandBoard.class);
        when(board.getId()).thenReturn(id);
        return board;
    }

    @Nested
    @DisplayName("cancelChunk - 실패")
    class CancelChunkTest {

        @Test
        void 취소_처리_수가_예상과_일치하지_않으면_IllegalStateException이_발생한다() {
            LocalDateTime threshold = LocalDateTime.now();
            List<DemandBoard> boards = List.of(boardWithId(10L), boardWithId(20L));
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(boards);
            when(productRepository.findProductIdsGroupedByBoardId(
                eq(List.of(10L, 20L)), eq(ProductStatus.BIDDING))).thenReturn(java.util.Map.of());
            when(demandBoardRepository.cancelBoardsAndFailDemands(List.of(10L, 20L), threshold))
                .thenReturn(1); // 예상 2, 실제 1

            assertThatThrownBy(() -> service.cancelChunk(threshold, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demand_board 취소 불일치");
        }

        @Test
        void 보드_상태_전이_수가_예상과_일치하지_않으면_IllegalStateException이_발생한다() {
            LocalDateTime threshold = LocalDateTime.now();
            List<DemandBoard> boards = List.of(boardWithId(10L), boardWithId(20L));
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(boards);
            when(productRepository.findProductIdsGroupedByBoardId(
                eq(List.of(10L, 20L)), eq(ProductStatus.BIDDING)))
                .thenReturn(java.util.Map.of(10L, List.of(100L), 20L, List.of(200L)));
            when(demandBoardRepository.transitionStatusBulk(
                anyList(),
                eq(DemandBoardStatus.GB_GATHERING),
                eq(DemandBoardStatus.GB_AWARDING),
                eq(threshold))).thenReturn(1); // 예상 2, 실제 1

            assertThatThrownBy(() -> service.cancelChunk(threshold, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demand_board 상태 전이 불일치");
        }

        @Test
        void 상품_상태_전이_수가_예상과_일치하지_않으면_IllegalStateException이_발생한다() {
            LocalDateTime threshold = LocalDateTime.now();
            List<DemandBoard> boards = List.of(boardWithId(10L));
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(boards);
            when(productRepository.findProductIdsGroupedByBoardId(
                eq(List.of(10L)), eq(ProductStatus.BIDDING)))
                .thenReturn(java.util.Map.of(10L, List.of(100L, 101L)));
            when(demandBoardRepository.transitionStatusBulk(
                anyList(),
                eq(DemandBoardStatus.GB_GATHERING),
                eq(DemandBoardStatus.GB_AWARDING),
                eq(threshold))).thenReturn(1);
            when(productRepository.transitionStatusBulk(
                anyList(), eq(ProductStatus.BIDDING), eq(ProductStatus.AWARDING), any()))
                .thenReturn(1); // 예상 2, 실제 1

            assertThatThrownBy(() -> service.cancelChunk(threshold, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("product 상태 전이 불일치");
        }
    }
}
