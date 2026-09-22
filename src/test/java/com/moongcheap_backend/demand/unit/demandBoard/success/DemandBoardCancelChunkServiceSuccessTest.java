package com.moongcheap_backend.demand.unit.demandBoard.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemandBoardCancelChunkServiceSuccessTest {

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
    @DisplayName("cancelChunk - 성공")
    class CancelChunkTest {

        @Test
        void lock_획득에_실패한다() {
            LocalDateTime threshold = LocalDateTime.now();
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(false);

            Optional<Integer> result = service.cancelChunk(threshold, 1000);

            assertThat(result).isEmpty();
            verify(demandBoardRepository, never()).findOverdueGatheringChunk(any(), anyInt());
        }

        @Test
        void 처리할_수요_보드가_없다() {
            LocalDateTime threshold = LocalDateTime.now();
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(List.of());

            Optional<Integer> result = service.cancelChunk(threshold, 1000);

            assertThat(result).contains(0);
        }

        @Test
        void 입찰_상품이_없는_보드들만_존재하여_취소_처리한다() {
            LocalDateTime threshold = LocalDateTime.now();
            List<DemandBoard> boards = List.of(boardWithId(10L), boardWithId(20L));
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(boards);
            when(productRepository.findProductIdsGroupedByBoardId(
                eq(List.of(10L, 20L)), eq(ProductStatus.BIDDING))).thenReturn(Map.of());
            when(demandBoardRepository.cancelBoardsAndFailDemands(List.of(10L, 20L), threshold))
                .thenReturn(2);

            Optional<Integer> result = service.cancelChunk(threshold, 1000);

            assertThat(result).contains(2);
            verify(demandBoardRepository).cancelBoardsAndFailDemands(List.of(10L, 20L), threshold);
            verify(demandBoardRepository, never()).transitionStatusBulk(
                anyList(), any(DemandBoardStatus.class), any(DemandBoardStatus.class), any());
        }

        @Test
        void 입찰_상품이_있는_보드들만_존재하여_낙찰_대기로_전환한다() {
            LocalDateTime threshold = LocalDateTime.now();
            List<DemandBoard> boards = List.of(boardWithId(10L), boardWithId(20L));
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(boards);
            when(productRepository.findProductIdsGroupedByBoardId(
                eq(List.of(10L, 20L)), eq(ProductStatus.BIDDING)))
                .thenReturn(Map.of(
                    10L, List.of(100L, 101L),
                    20L, List.of(200L)
                ));
            when(demandBoardRepository.transitionStatusBulk(
                eq(List.of(10L, 20L)),
                eq(DemandBoardStatus.GB_GATHERING),
                eq(DemandBoardStatus.GB_AWARDING),
                eq(threshold))).thenReturn(2);
            when(productRepository.transitionStatusBulk(
                anyList(), eq(ProductStatus.BIDDING), eq(ProductStatus.AWARDING), eq(threshold)))
                .thenReturn(3);

            Optional<Integer> result = service.cancelChunk(threshold, 1000);

            assertThat(result).contains(2);
            verify(demandBoardRepository).transitionStatusBulk(
                anyList(),
                eq(DemandBoardStatus.GB_GATHERING),
                eq(DemandBoardStatus.GB_AWARDING),
                eq(threshold));
            verify(productRepository).transitionStatusBulk(
                anyList(), eq(ProductStatus.BIDDING), eq(ProductStatus.AWARDING), eq(threshold));
            verify(demandBoardRepository, never()).cancelBoardsAndFailDemands(anyList(), any());
        }

        @Test
        void 취소_대상과_전환_대상이_혼재한다() {
            LocalDateTime threshold = LocalDateTime.now();
            List<DemandBoard> boards = List.of(boardWithId(10L), boardWithId(20L));
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_BOARD_CANCEL_BATCH))
                .thenReturn(true);
            when(demandBoardRepository.findOverdueGatheringChunk(threshold, 1000))
                .thenReturn(boards);
            // board 10L만 productIds 있음
            when(productRepository.findProductIdsGroupedByBoardId(
                eq(List.of(10L, 20L)), eq(ProductStatus.BIDDING)))
                .thenReturn(Map.of(10L, List.of(100L)));
            when(demandBoardRepository.cancelBoardsAndFailDemands(List.of(20L), threshold))
                .thenReturn(1);
            when(demandBoardRepository.transitionStatusBulk(
                eq(List.of(10L)),
                eq(DemandBoardStatus.GB_GATHERING),
                eq(DemandBoardStatus.GB_AWARDING),
                eq(threshold))).thenReturn(1);
            when(productRepository.transitionStatusBulk(
                anyList(), eq(ProductStatus.BIDDING), eq(ProductStatus.AWARDING), eq(threshold)))
                .thenReturn(1);

            Optional<Integer> result = service.cancelChunk(threshold, 1000);

            assertThat(result).contains(2);
            verify(demandBoardRepository).cancelBoardsAndFailDemands(List.of(20L), threshold);
            verify(demandBoardRepository).transitionStatusBulk(
                eq(List.of(10L)),
                eq(DemandBoardStatus.GB_GATHERING),
                eq(DemandBoardStatus.GB_AWARDING),
                eq(threshold));
        }
    }
}
