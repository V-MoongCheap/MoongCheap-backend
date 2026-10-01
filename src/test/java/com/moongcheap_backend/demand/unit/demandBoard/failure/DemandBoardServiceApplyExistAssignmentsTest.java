package com.moongcheap_backend.demand.unit.demandBoard.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import com.moongcheap_backend.demand.application.demandBoard.DemandBoardService;
import com.moongcheap_backend.demand.application.demandBoard.StaleFormationItemException;
import com.moongcheap_backend.demand.infrastructure.demand.DemandBatchRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.ExistingBoardAssignment;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto;
import com.moongcheap_backend.groupbuy.application.GroupBuyService;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productAwardEvaluation.ProductAwardEvaluationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemandBoardService.applyExistAssignments")
class DemandBoardServiceApplyExistAssignmentsTest {

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

    /**
     * 11개 assignment → 2 chunks (boardId 기준 정렬 후) chunk[0]: boardId 1~10, demand 각 1개 →
     * chunkDemandTotal = 10 chunk[1]: boardId 11,   demand 1개   → chunkDemandTotal = 1
     */
    private FormationPlanRequestDto twoChunkRequest() {
        List<ExistingBoardAssignment> assignments = IntStream.rangeClosed(1, 11)
            .mapToObj(i -> new ExistingBoardAssignment((long) i, List.of((long) i)))
            .toList();
        return new FormationPlanRequestDto("v1", OffsetDateTime.now(), "r1", assignments,
            List.of());
    }

    @Nested
    @DisplayName("CHUNK 일부만 실패 - 첫 chunk 성공, 두 번째 chunk 실패")
    class PartialChunkFailureTest {

        @Test
        void 두번째_chunk가_StaleFormationItemException으로_실패한다() {
            doNothing()
                .doThrow(new StaleFormationItemException())
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(twoChunkRequest());

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(10);
            assertThat(result.existingAssignments().staleCount()).isEqualTo(1);
        }

        @Test
        void 두번째_chunk가_DataAccessException으로_실패한다() {
            doNothing()
                .doThrow(new DataIntegrityViolationException("db error"))
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(twoChunkRequest());

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(10);
            assertThat(result.existingAssignments().staleCount()).isEqualTo(1);
        }

        @Test
        void 두번째_chunk가_RuntimeException으로_실패한다() {
            doNothing()
                .doThrow(new RuntimeException("unexpected error"))
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(twoChunkRequest());

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(10);
            assertThat(result.existingAssignments().staleCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("CHUNK 모두 실패")
    class AllChunkFailureTest {

        @Test
        void 모든_chunk가_StaleFormationItemException으로_실패한다() {
            doThrow(new StaleFormationItemException())
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(twoChunkRequest());

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(0);
            assertThat(result.existingAssignments().staleCount()).isEqualTo(11);
        }

        @Test
        void 모든_chunk가_DataAccessException으로_실패한다() {
            doThrow(new DataIntegrityViolationException("db error"))
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(twoChunkRequest());

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(0);
            assertThat(result.existingAssignments().staleCount()).isEqualTo(11);
        }

        @Test
        void 모든_chunk가_RuntimeException으로_실패한다() {
            doThrow(new RuntimeException("unexpected error"))
                .when(mockSelf).applyExistingAssignmentChunk(anyList(), any());

            FormationPlanResponseDto result = service.applyFormationPlan(twoChunkRequest());

            assertThat(result.status()).isEqualTo(FormationPlanResponseDto.Status.APPLIED);
            assertThat(result.existingAssignments().appliedCount()).isEqualTo(0);
            assertThat(result.existingAssignments().staleCount()).isEqualTo(11);
        }
    }
}
