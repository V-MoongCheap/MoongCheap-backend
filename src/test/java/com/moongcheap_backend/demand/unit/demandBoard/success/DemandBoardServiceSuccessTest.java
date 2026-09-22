package com.moongcheap_backend.demand.unit.demandBoard.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService;
import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService.AwardingChunkResult;
import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService.SubstituteOfferChunkResult;
import com.moongcheap_backend.demand.application.demandBoard.StaleFormationItemException;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandBatchRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.AuctionResultRow;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AuctionResultDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto.BoardResult;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto.Evaluation;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.CatalogDemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardJoinRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardSummaryDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.ExistingBoardAssignment;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.NewBoard;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.NewBoardResult;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.NewBoardStatus;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto.Proposal;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanResponseDto;
import com.moongcheap_backend.groupbuy.application.GroupBuyService;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productAwardEvaluation.ProductAwardEvaluationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DemandBoardServiceSuccessTest {

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

    @Mock
    private DemandBoardService mockSelf;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "self", mockSelf);
    }

    private DemandBoardSummaryDto summary(Long id) {
        return new DemandBoardSummaryDto(
            id, 10L, "url", "name", 5, 2, 10000, 20000, LocalDateTime.now().plusDays(1));
    }

    @Nested
    @DisplayName("hasProductBoard - 성공")
    class HasProductBoardTest {

        @Test
        void GB_GATHERING_상태의_수요_보드가_존재한다() {
            when(demandBoardRepository.existsByCatalogIdAndStatusIn(
                eq(10L), anyList())).thenReturn(true);

            assertThat(service.hasProductBoard(10L)).isTrue();
        }

        @Test
        void 해당_status의_수요_보드가_없다() {
            when(demandBoardRepository.existsByCatalogIdAndStatusIn(
                eq(10L), anyList())).thenReturn(false);

            assertThat(service.hasProductBoard(10L)).isFalse();
        }
    }

    @Nested
    @DisplayName("getHostDemandBoard - 성공")
    class GetHostDemandBoardTest {

        @Test
        void 판매자용_수요_보드_목록을_조회한다() {
            Pageable pageable = PageRequest.of(0, 10);
            when(demandBoardQueryRepository.getDemandBoardItems(
                eq(List.of(DemandBoardStatus.GB_GATHERING)), eq(pageable)))
                .thenReturn(List.of(summary(1L)));

            DemandBoardListDto result = service.getHostDemandBoard(pageable);

            assertThat(result.demandBoards()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getById - 성공")
    class GetByIdTest {

        @Test
        void 참여_중인_수요_보드를_단건_조회한다() {
            when(demandBoardQueryRepository.getDemandBoardItemsById(1L))
                .thenReturn(Optional.of(summary(1L)));
            when(demandRepository.existsByMemberIdAndDemandBoardIdAndStatusIn(
                eq(10L), eq(1L), anyList())).thenReturn(true);

            DemandBoardDto result = service.getById(10L, 1L);

            assertThat(result.isParticipating()).isTrue();
        }

        @Test
        void 참여하지_않은_수요_보드를_단건_조회한다() {
            when(demandBoardQueryRepository.getDemandBoardItemsById(1L))
                .thenReturn(Optional.of(summary(1L)));
            when(demandRepository.existsByMemberIdAndDemandBoardIdAndStatusIn(
                eq(10L), eq(1L), anyList())).thenReturn(false);

            DemandBoardDto result = service.getById(10L, 1L);

            assertThat(result.isParticipating()).isFalse();
        }
    }

    @Nested
    @DisplayName("getByCatalogId - 성공")
    class GetByCatalogIdTest {

        @Test
        void 페이지_크기_이상이면_hasNext가_true이다() {
            Pageable pageable = PageRequest.of(0, 1);
            CatalogDemandBoardListDto.DemandBoardCardDto card1 =
                mock(CatalogDemandBoardListDto.DemandBoardCardDto.class);
            CatalogDemandBoardListDto.DemandBoardCardDto card2 =
                mock(CatalogDemandBoardListDto.DemandBoardCardDto.class);
            when(demandBoardQueryRepository.getDemandBoardsByCatalogId(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(card1, card2));

            CatalogDemandBoardListDto result =
                service.getByCatalogId(1L, 10L, pageable, null, null);

            assertThat(result.hasNext()).isTrue();
        }
    }

    @Nested
    @DisplayName("getAuctionResult - 성공")
    class GetAuctionResultTest {

        @Test
        void 낙찰_결과를_조회한다() {
            AuctionResultRow row = new AuctionResultRow(
                DemandStatus.PAYMENT_PENDING,
                "카탈로그",
                "thumb.png",
                10000,
                3000,
                "판매자",
                1,
                5,
                5L,
                null,
                "낙찰 사유"
            );
            when(demandBoardQueryRepository.getAuctionResult(1L, 10L))
                .thenReturn(Optional.of(row));

            AuctionResultDto result = service.getAuctionResult(10L, 1L);

            assertThat(result.demandStatus()).isEqualTo(DemandStatus.PAYMENT_PENDING);
            assertThat(result.catalogName()).isEqualTo("카탈로그");
            assertThat(result.paymentDeadlineAt()).isNull();
        }
    }

    @Nested
    @DisplayName("getPendingAwarding - 성공")
    class GetPendingAwardingTest {

        @Test
        void 판정_대기_보드_목록을_조회한다() {
            when(demandBoardQueryRepository.getPendingAwardingBoards(11))
                .thenReturn(List.of());

            assertThat(service.getPendingAwarding(10)).isNotNull();
        }
    }

    @Nested
    @DisplayName("join - 성공")
    class JoinTest {

        @Test
        void 유효한_payMethod와_활성_보드에_참여한다() {
            Long memberId = 1L;
            Long boardId = 100L;
            DemandBoard board = mock(DemandBoard.class);
            when(board.getId()).thenReturn(boardId);
            when(board.getCatalogId()).thenReturn(10L);
            when(board.getPriceMin()).thenReturn(10000);
            when(board.getPriceMax()).thenReturn(20000);
            when(board.getSaleEndAt()).thenReturn(LocalDateTime.now().plusDays(1));

            when(brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
                20L, memberId, PaymentsMethodStatus.ACTIVE)).thenReturn(true);
            when(demandBoardRepository.findByIdAndStatusInForUpdate(eq(boardId), anyList()))
                .thenReturn(Optional.of(board));
            Demand saved = mock(Demand.class);
            when(demandRepository.saveAndFlush(any(Demand.class))).thenReturn(saved);

            DemandBoardJoinRequestDto request = new DemandBoardJoinRequestDto(
                20L, 1, false, null, true, true, true, true);

            service.join(memberId, boardId, request);

            verify(demandRepository).saveAndFlush(any(Demand.class));
            verify(board).increaseParticipantCount();
        }
    }

    @Nested
    @DisplayName("applyExistingAssignmentChunk - 성공")
    class ApplyExistingAssignmentChunkTest {

        @Test
        void 배치_배정_업데이트가_모두_성공한다() {
            List<ExistingBoardAssignment> assignments = List.of(
                new ExistingBoardAssignment(1L, List.of(10L, 11L)),
                new ExistingBoardAssignment(2L, List.of(20L))
            );
            when(demandBatchRepository.batchAssignToExistingBoard(eq(assignments), any()))
                .thenReturn(new int[]{2, 1});

            service.applyExistingAssignmentChunk(assignments, LocalDateTime.now());

            verify(demandBatchRepository).batchAssignToExistingBoard(eq(assignments), any());
        }
    }

    @Nested
    @DisplayName("createNewBoardChunk - 성공")
    class CreateNewBoardChunkTest {

        @Test
        void 새_보드_생성과_배정이_모두_성공한다() {
            List<NewBoard> newBoards = List.of(
                new NewBoard("key1", 10L, 10000, 20000, List.of(1L, 2L))
            );
            DemandBoard saved = mock(DemandBoard.class);
            when(saved.getId()).thenReturn(100L);
            when(saved.getSaleEndAt()).thenReturn(LocalDateTime.now().plusDays(5));
            when(demandBoardRepository.save(any(DemandBoard.class))).thenReturn(saved);
            when(demandBatchRepository.batchAssignToBoard(anyList(), any()))
                .thenReturn(new int[]{2});

            List<NewBoardResult> results =
                service.createNewBoardChunk(newBoards, LocalDateTime.now());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(NewBoardStatus.CREATED);
            assertThat(results.get(0).demandBoardId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("substituteOfferChunk - 성공")
    class SubstituteOfferChunkTest {

        @Test
        void 유효한_proposal로_대체_제안을_적용한다() {
            Proposal proposal = new Proposal(1L, 10L, 20L, 100L);
            Demand demand = mock(Demand.class);
            when(demand.getCatalogId()).thenReturn(10L);
            when(demand.getStatus()).thenReturn(DemandStatus.UNASSIGNED);
            when(demand.getDemandBoardId()).thenReturn(null);
            when(demandBoardRepository.existsByIdAndStatusAndCatalogId(
                100L, DemandBoardStatus.GB_GATHERING, 20L)).thenReturn(true);
            when(demandRepository.findByIdAndSubstitutableForUpdate(1L))
                .thenReturn(Optional.of(demand));

            SubstituteOfferChunkResult result =
                service.substituteOfferChunk(List.of(proposal));

            assertThat(result.applied()).isEqualTo(1);
            assertThat(result.alreadyApplied()).isEqualTo(0);
            assertThat(result.staleRejected()).isEqualTo(0);
            verify(demand).substituteOffer(100L);
        }

        @Test
        void 이미_대체_제안이_적용된_proposal을_처리하면_alreadyApplied가_증가한다() {
            Proposal proposal = new Proposal(1L, 10L, 20L, 100L);
            Demand demand = mock(Demand.class);
            when(demand.getCatalogId()).thenReturn(10L);
            when(demand.getStatus()).thenReturn(DemandStatus.SUBSTITUTE_OFFERED);
            when(demand.getDemandBoardId()).thenReturn(100L);
            when(demandBoardRepository.existsByIdAndStatusAndCatalogId(
                100L, DemandBoardStatus.GB_GATHERING, 20L)).thenReturn(true);
            when(demandRepository.findByIdAndSubstitutableForUpdate(1L))
                .thenReturn(Optional.of(demand));

            SubstituteOfferChunkResult result =
                service.substituteOfferChunk(List.of(proposal));

            assertThat(result.alreadyApplied()).isEqualTo(1);
        }

        @Test
        void 보드를_찾을_수_없는_proposal은_staleRejected가_증가한다() {
            Proposal proposal = new Proposal(1L, 10L, 20L, 100L);
            when(demandBoardRepository.existsByIdAndStatusAndCatalogId(
                100L, DemandBoardStatus.GB_GATHERING, 20L)).thenReturn(false);

            SubstituteOfferChunkResult result =
                service.substituteOfferChunk(List.of(proposal));

            assertThat(result.staleRejected()).isEqualTo(1);
        }

        @Test
        void expectedOriginalCatalogId가_불일치하면_staleRejected가_증가한다() {
            Proposal proposal = new Proposal(1L, 10L, 20L, 100L);
            Demand demand = mock(Demand.class);
            when(demand.getCatalogId()).thenReturn(99L); // expected=10L 불일치
            when(demandBoardRepository.existsByIdAndStatusAndCatalogId(
                100L, DemandBoardStatus.GB_GATHERING, 20L)).thenReturn(true);
            when(demandRepository.findByIdAndSubstitutableForUpdate(1L))
                .thenReturn(Optional.of(demand));

            SubstituteOfferChunkResult result =
                service.substituteOfferChunk(List.of(proposal));

            assertThat(result.staleRejected()).isEqualTo(1);
        }

        @Test
        void 이미_다른_보드에_배정된_수요는_staleRejected가_증가한다() {
            Proposal proposal = new Proposal(1L, 10L, 20L, 100L);
            Demand demand = mock(Demand.class);
            when(demand.getCatalogId()).thenReturn(10L);
            when(demand.getStatus()).thenReturn(DemandStatus.UNASSIGNED);
            when(demand.getDemandBoardId()).thenReturn(50L); // 다른 보드에 이미 배정
            when(demandBoardRepository.existsByIdAndStatusAndCatalogId(
                100L, DemandBoardStatus.GB_GATHERING, 20L)).thenReturn(true);
            when(demandRepository.findByIdAndSubstitutableForUpdate(1L))
                .thenReturn(Optional.of(demand));

            SubstituteOfferChunkResult result =
                service.substituteOfferChunk(List.of(proposal));

            assertThat(result.staleRejected()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("awardChunk - 성공")
    class AwardChunkTest {

        @Test
        void 낙찰자가_있는_정상_낙찰_결과를_적용한다() {
            Evaluation winner = new Evaluation(1L, BigDecimal.valueOf(0.9), "good", true);
            Evaluation loser = new Evaluation(2L, BigDecimal.valueOf(0.1), "bad", false);
            BoardResult br = new BoardResult(100L, LocalDateTime.now(), List.of(winner, loser));

            when(demandBoardRepository.markAwarded(
                eq(100L), eq(DemandBoardStatus.GB_AWARDING),
                eq(DemandBoardStatus.GB_ACTION_REQUIRED), any())).thenReturn(1);
            when(demandRepository.transitionStatusBulkByBoardIds(
                eq(List.of(100L)), eq(DemandStatus.ASSIGNED),
                eq(DemandStatus.PAYMENT_PENDING), any())).thenReturn(1);
            when(productRepository.transitionStatusForBoard(
                eq(1L), eq(100L), eq(ProductStatus.AWARDING),
                eq(ProductStatus.AWARDED), any())).thenReturn(1);
            when(productRepository.transitionStatusBulkForBoard(
                anyList(), anyLong(), any(ProductStatus.class), any(ProductStatus.class),
                any(LocalDateTime.class))).thenReturn(1);

            AwardingChunkResult result = service.awardChunk(List.of(br));

            assertThat(result.applied()).isEqualTo(1);
            assertThat(result.staleRejected()).isEqualTo(0);
            verify(groupBuyService).createGroupBuy(1L);
            verify(productAwardEvaluationRepository).saveAll(anyList());
        }

        @Test
        void 낙찰자가_없는_유찰_결과를_적용한다() {
            Evaluation loser1 = new Evaluation(1L, BigDecimal.valueOf(0.1), "bad", false);
            Evaluation loser2 = new Evaluation(2L, BigDecimal.valueOf(0.2), "bad", false);
            BoardResult br = new BoardResult(100L, LocalDateTime.now(), List.of(loser1, loser2));

            when(demandBoardRepository.markAwarded(
                eq(100L), eq(DemandBoardStatus.GB_AWARDING),
                eq(DemandBoardStatus.GB_CANCELED), any())).thenReturn(1);
            when(demandRepository.transitionStatusBulkByBoardIds(
                eq(List.of(100L)), eq(DemandStatus.ASSIGNED),
                eq(DemandStatus.FAILED), any())).thenReturn(1);
            when(productRepository.transitionStatusBulkForBoard(
                anyList(), anyLong(), any(ProductStatus.class), any(ProductStatus.class),
                any(LocalDateTime.class))).thenReturn(2);

            AwardingChunkResult result = service.awardChunk(List.of(br));

            assertThat(result.applied()).isEqualTo(1);
            verify(groupBuyService, org.mockito.Mockito.never()).createGroupBuy(any());
        }

        @Test
        void 보드를_찾을_수_없으면_staleRejected가_증가한다() {
            Evaluation eval = new Evaluation(1L, BigDecimal.valueOf(0.5), "reason", true);
            BoardResult br = new BoardResult(100L, LocalDateTime.now(), List.of(eval));

            when(demandBoardRepository.markAwarded(
                eq(100L), any(), any(), any())).thenReturn(0);

            AwardingChunkResult result = service.awardChunk(List.of(br));

            assertThat(result.applied()).isEqualTo(0);
            assertThat(result.staleRejected()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("applyFormationPlan - 성공")
    class ApplyFormationPlanTest {

        private FormationPlanRequestDto request(int assignmentCount, int newBoardCount) {
            List<ExistingBoardAssignment> assignments = java.util.stream.IntStream
                .rangeClosed(1, assignmentCount)
                .mapToObj(i -> new ExistingBoardAssignment((long) i, List.of((long) i)))
                .toList();
            List<NewBoard> newBoards = java.util.stream.IntStream
                .rangeClosed(1, newBoardCount)
                .mapToObj(i -> new NewBoard("k" + i, (long) i, 100, 200, List.of((long) (i + 100))))
                .toList();
            return new FormationPlanRequestDto("v1", OffsetDateTime.now(), "r1", assignments,
                newBoards);
        }

        @Test
        void 배정과_새_보드_생성이_모두_성공한다() {
            when(mockSelf.createNewBoardChunk(anyList(), any()))
                .thenReturn(List.of(new NewBoardResult("k1", 100L, NewBoardStatus.CREATED)));

            FormationPlanResponseDto result = service.applyFormationPlan(request(1, 1));

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(1);
            assertThat(result.newBoards()).hasSize(1);
            assertThat(result.newBoards().get(0).status()).isEqualTo(NewBoardStatus.CREATED);
        }

        @Test
        void 배정_chunk가_stale로_실패한다() {
            doThrow(new StaleFormationItemException())
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(request(1, 0));

            assertThat(result.existingAssignments().staleCount()).isEqualTo(1);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(0);
        }

        @Test
        void 새_보드_chunk가_stale로_실패한다() {
            when(mockSelf.createNewBoardChunk(anyList(), any()))
                .thenThrow(new StaleFormationItemException());

            FormationPlanResponseDto result = service.applyFormationPlan(request(0, 1));

            assertThat(result.newBoards()).hasSize(1);
            assertThat(result.newBoards().get(0).status()).isEqualTo(NewBoardStatus.STALE_REJECTED);
        }

        @Test
        void DB_예외로_chunk가_실패한다() {
            doThrow(new DataIntegrityViolationException("db"))
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(request(1, 0));

            assertThat(result.existingAssignments().staleCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("applyAwardingResult - 성공")
    class ApplyAwardingResultTest {

        private AwardingResultRequestDto request() {
            Evaluation eval = new Evaluation(1L, BigDecimal.valueOf(0.9), "r", true);
            BoardResult br = new BoardResult(100L, LocalDateTime.now(), List.of(eval));
            return new AwardingResultRequestDto("v1", OffsetDateTime.now(), "r1", List.of(br));
        }

        @Test
        void 낙찰_결과를_일괄_적용한다() {
            when(mockSelf.awardChunk(anyList()))
                .thenReturn(new AwardingChunkResult(1, 0));

            AwardingResultResponseDto result = service.applyAwardingResult(request());

            assertThat(result.status()).isEqualTo(AwardingResultResponseDto.Status.APPLIED);
            assertThat(result.appliedCount()).isEqualTo(1);
        }

        @Test
        void DB_예외로_낙찰_chunk가_실패한다() {
            when(mockSelf.awardChunk(anyList()))
                .thenThrow(new DataIntegrityViolationException("db"));

            AwardingResultResponseDto result = service.applyAwardingResult(request());

            assertThat(result.staleRejectedCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("applySubstituteOfferPlan - 성공")
    class ApplySubstituteOfferPlanTest {

        @Test
        void 대체_제안_계획을_일괄_적용한다() {
            SubstituteOfferPlanRequestDto request = new SubstituteOfferPlanRequestDto(
                "v1", OffsetDateTime.now(), "r1",
                List.of(new Proposal(1L, 10L, 20L, 100L))
            );
            when(mockSelf.substituteOfferChunk(anyList()))
                .thenReturn(new SubstituteOfferChunkResult(1, 0, 0));

            SubstituteOfferPlanResponseDto result =
                service.applySubstituteOfferPlan(request);

            assertThat(result.appliedCount()).isEqualTo(1);
        }
    }
}
