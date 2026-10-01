package com.moongcheap_backend.demand.unit.demandBoard.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService;
import com.moongcheap_backend.demand.application.demandBoard.StaleFormationItemException;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.infrastructure.demand.DemandBatchRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto.BoardResult;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto.Evaluation;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardJoinRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.ExistingBoardAssignment;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.NewBoard;
import com.moongcheap_backend.groupbuy.application.GroupBuyService;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productAwardEvaluation.ProductAwardEvaluationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DemandBoardServiceFailureTest {

    @Mock
    private DemandBoardRepository demandBoardRepository;
    @Mock
    private DemandBoardQueryRepository demandBoardQueryRepository;
    @Mock
    private DemandRepository demandRepository;
    @Mock
    private DemandBatchRepository demandBatchRepository;
    @Mock
    private ProductAwardEvaluationRepository productAwardEvaluationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private GroupBuyService groupBuyService;
    @Mock
    private BrandPayMethodRepository brandPayMethodRepository;

    @InjectMocks
    private DemandBoardService service;

    private DemandBoardJoinRequestDto joinRequest() {
        return new DemandBoardJoinRequestDto(20L, 1, false, null, true, true, true, true);
    }

    private DemandBoard boardWith(LocalDateTime saleEndAt) {
        DemandBoard board = mock(DemandBoard.class);
        lenient().when(board.getId()).thenReturn(100L);
        lenient().when(board.getCatalogId()).thenReturn(10L);
        lenient().when(board.getPriceMin()).thenReturn(10000);
        lenient().when(board.getPriceMax()).thenReturn(20000);
        when(board.getSaleEndAt()).thenReturn(saleEndAt);
        return board;
    }

    @Nested
    @DisplayName("getById - 실패")
    class GetByIdTest {

        @Test
        void 존재하지_않는_수요_보드를_조회한다() {
            when(demandBoardQueryRepository.getDemandBoardItemsById(1L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_BOARD_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAuctionResult - 실패")
    class GetAuctionResultTest {

        @Test
        void 존재하지_않는_보드의_낙찰_결과를_조회한다() {
            when(demandBoardQueryRepository.getAuctionResult(1L, 10L))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAuctionResult(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_BOARD_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("join - 실패")
    class JoinTest {

        @Test
        void 유효하지_않은_payMethod로_참여한다() {
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(false);

            assertThatThrownBy(() -> service.join(1L, 100L, joinRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BRAND_PAY_METHOD_NOT_FOUND);
        }

        @Test
        void GB_GATHERING_상태가_아닌_수요_보드에_참여한다() {
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            when(demandBoardRepository.findByIdAndStatusInForUpdate(eq(100L), anyList()))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.join(1L, 100L, joinRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_BOARD_NOT_FOUND);
        }

        @Test
        void saleEndAt이_null인_수요_보드에_참여한다() {
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            DemandBoard board = boardWith(null);
            when(demandBoardRepository.findByIdAndStatusInForUpdate(eq(100L), anyList()))
                .thenReturn(Optional.of(board));

            assertThatThrownBy(() -> service.join(1L, 100L, joinRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_BOARD_CLOSED);
        }

        @Test
        void 판매_마감된_수요_보드에_참여한다() {
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            DemandBoard board = boardWith(LocalDateTime.now().minusDays(1));
            when(demandBoardRepository.findByIdAndStatusInForUpdate(eq(100L), anyList()))
                .thenReturn(Optional.of(board));

            assertThatThrownBy(() -> service.join(1L, 100L, joinRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_BOARD_CLOSED);
        }

        @Test
        void 이미_참여한_수요_보드에_다시_참여한다() {
            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, 1L, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            DemandBoard board = boardWith(LocalDateTime.now().plusDays(1));
            when(demandBoardRepository.findByIdAndStatusInForUpdate(eq(100L), anyList()))
                .thenReturn(Optional.of(board));
            when(demandRepository.saveAndFlush(any(Demand.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

            assertThatThrownBy(() -> service.join(1L, 100L, joinRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("applyExistingAssignmentChunk - 실패")
    class ApplyExistingAssignmentChunkTest {

        @Test
        void 배치_배정_업데이트_수가_예상과_일치하지_않으면_StaleFormationItemException이_발생한다() {
            List<ExistingBoardAssignment> assignments = List.of(
                new ExistingBoardAssignment(1L, List.of(10L, 11L))
            );
            when(demandBatchRepository.batchAssignToExistingBoard(eq(assignments), any()))
                .thenReturn(new int[]{1}); // 예상 2, 실제 1

            assertThatThrownBy(() ->
                service.applyExistingAssignmentChunk(assignments, LocalDateTime.now()))
                .isInstanceOf(StaleFormationItemException.class);
        }
    }

    @Nested
    @DisplayName("createNewBoardChunk - 실패")
    class CreateNewBoardChunkTest {

        @Test
        void 새_보드_배정_업데이트_수가_예상과_일치하지_않으면_StaleFormationItemException이_발생한다() {
            List<NewBoard> newBoards = List.of(
                new NewBoard("key1", 10L, 10000, 20000, List.of(1L, 2L))
            );
            DemandBoard saved = mock(DemandBoard.class);
            when(saved.getId()).thenReturn(100L);
            when(saved.getSaleEndAt()).thenReturn(LocalDateTime.now().plusDays(5));
            when(demandBoardRepository.save(any(DemandBoard.class))).thenReturn(saved);
            when(demandBatchRepository.batchAssignToBoard(anyList(), any()))
                .thenReturn(new int[]{1}); // 예상 2, 실제 1

            assertThatThrownBy(() ->
                service.createNewBoardChunk(newBoards, LocalDateTime.now()))
                .isInstanceOf(StaleFormationItemException.class);
        }
    }

    @Nested
    @DisplayName("awardChunk - 실패")
    class AwardChunkTest {

        @Test
        void 보드는_전이되었지만_demand가_하나도_전이되지_않으면_DEMAND_BOARD_NO_PARTICIPANT() {
            Evaluation eval = new Evaluation(1L, BigDecimal.valueOf(0.9), "r", true);
            BoardResult br = new BoardResult(100L, LocalDateTime.now(), List.of(eval));

            when(demandBoardRepository.markAwarded(eq(100L), any(), any(), any()))
                .thenReturn(1);
            when(demandRepository.transitionStatusBulkByBoardIds(
                anyList(), any(), any(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.awardChunk(List.of(br)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEMAND_BOARD_NO_PARTICIPANT);
        }

        @Test
        void 상품_전이_수가_evaluation_수와_일치하지_않으면_DEMAND_BOARD_AWARDING_INCONSISTENT() {
            Evaluation winner = new Evaluation(1L, BigDecimal.valueOf(0.9), "r", true);
            Evaluation loser = new Evaluation(2L, BigDecimal.valueOf(0.1), "r", false);
            BoardResult br = new BoardResult(100L, LocalDateTime.now(), List.of(winner, loser));

            when(demandBoardRepository.markAwarded(eq(100L), any(), any(), any()))
                .thenReturn(1);
            when(demandRepository.transitionStatusBulkByBoardIds(
                anyList(), any(), any(), any())).thenReturn(1);
            // winner update = 1, loser update = 0 → total 1 != evaluations.size(2)
            when(productRepository.transitionStatusForBoard(
                eq(1L), eq(100L), eq(ProductStatus.AWARDING),
                eq(ProductStatus.AWARDED), any())).thenReturn(1);
            when(productRepository.transitionStatusBulkForBoard(
                anyList(), anyLong(), any(ProductStatus.class), any(ProductStatus.class),
                any(LocalDateTime.class))).thenReturn(0);

            assertThatThrownBy(() -> service.awardChunk(List.of(br)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                    "errorCode", ErrorCode.DEMAND_BOARD_AWARDING_INCONSISTENT);
        }
    }
}
