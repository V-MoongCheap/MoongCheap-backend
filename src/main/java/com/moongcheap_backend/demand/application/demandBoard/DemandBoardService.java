package com.moongcheap_backend.demand.application.demandBoard;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.schema.InternalSchemaVersions;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandBatchRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandBatchRepository.AssignToBoardArgs;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AuctionResultDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingPendingResponseDto;
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
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.ExistingAssignments;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.NewBoardResult;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.NewBoardStatus;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.Status;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto.Proposal;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanResponseDto;
import com.moongcheap_backend.groupbuy.application.GroupBuyService;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.domain.productAwardEvaluation.ProductAwardEvaluation;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productAwardEvaluation.ProductAwardEvaluationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemandBoardService {

    private final DemandBoardRepository demandBoardRepository;
    private final DemandBoardQueryRepository demandBoardQueryRepository;
    private final DemandRepository demandRepository;
    private final DemandBatchRepository demandBatchRepository;
    private final ProductAwardEvaluationRepository productAwardEvaluationRepository;
    private final ProductRepository productRepository;
    private final GroupBuyService groupBuyService;
    private final BrandPayMethodRepository brandPayMethodRepository;

    @Lazy
    @Autowired
    private DemandBoardService self;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public boolean hasProductBoard(Long productBoardId) {
        return demandBoardRepository.existsByCatalogIdAndStatusIn(
            productBoardId,
            List.of(DemandBoardStatus.GB_ACTION_REQUIRED, DemandBoardStatus.GB_GATHERING));
    }

    @Transactional(readOnly = true)
    public DemandBoardListDto getHostDemandBoard(Pageable pageable) {
        return new DemandBoardListDto(demandBoardQueryRepository.getDemandBoardItems(
            List.of(DemandBoardStatus.GB_GATHERING), pageable));
    }

    @Transactional(readOnly = true)
    public DemandBoardDto getById(Long memberId, Long demandBoardId) {
        DemandBoardSummaryDto summary = demandBoardQueryRepository.getDemandBoardItemsById(
                demandBoardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND));
        boolean isParticipating = demandRepository.existsByMemberIdAndDemandBoardIdAndStatusIn(
            memberId, summary.demandBoardId(),
            List.of(DemandStatus.ASSIGNED, DemandStatus.PAYMENT_PENDING)
        );
        return DemandBoardDto.from(summary, isParticipating);
    }

    @Transactional(readOnly = true)
    public CatalogDemandBoardListDto getByCatalogId(
        Long memberId, Long catalogId, Pageable pageable, Integer minPrice, Integer maxPrice) {
        Pageable fetchPageable = PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize() + 1, pageable.getSort());
        List<CatalogDemandBoardListDto.DemandBoardCardDto> items =
            demandBoardQueryRepository.getDemandBoardsByCatalogId(
                catalogId, memberId, fetchPageable, minPrice, maxPrice);
        return CatalogDemandBoardListDto.of(items, pageable);
    }

    @Transactional(readOnly = true)
    public AuctionResultDto getAuctionResult(Long memberId, Long demandBoardId) {
        return demandBoardQueryRepository.getAuctionResult(demandBoardId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public AwardingPendingResponseDto getPendingAwarding(int size) {
        return AwardingPendingResponseDto.of(
            InternalSchemaVersions.AWARDING_PENDING,
            LocalDateTime.now(),
            demandBoardQueryRepository.getPendingAwardingBoards(size + 1),
            size
        );
    }

    @Transactional
    public Long join(Long memberId, Long demandBoardId, DemandBoardJoinRequestDto request) {
        if (!brandPayMethodRepository.existsByIdAndMemberIdAndStatus(
            request.payMethodId(), memberId, PaymentsMethodStatus.ACTIVE
        )) {
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_NOT_FOUND);
        }
        DemandBoard demandBoard = demandBoardRepository.findByIdAndStatusInForUpdate(demandBoardId,
                List.of(DemandBoardStatus.GB_GATHERING))
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND));
        if (demandBoard.getSaleEndAt() == null
            || demandBoard.getSaleEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_CLOSED);
        }
        Demand demand = Demand.boardJoinBuilder()
            .demandBoardId(demandBoard.getId())
            .memberId(memberId)
            .catalogId(demandBoard.getCatalogId())
            .payMethodId(request.payMethodId())
            .desiredPriceMin(demandBoard.getPriceMin())
            .desiredPriceMax(demandBoard.getPriceMax())
            .desireEndAt(demandBoard.getSaleEndAt())
            .quantity(request.quantity())
            .isSubstitutable(request.isSubstitutable())
            .extraRequirement(request.extraRequirement())
            .build();
        try {
            demandRepository.saveAndFlush(demand);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DEMAND_ALREADY_EXISTS);
        }
        demandBoard.increaseParticipantCount();
        return demand.getId();
    }

    private static final int FORMATION_PLAN_CHUNK_SIZE = 10;

    //완료
    public FormationPlanResponseDto applyFormationPlan(FormationPlanRequestDto request) {
        LocalDateTime now = LocalDateTime.now();
        return new FormationPlanResponseDto(
            Status.APPLIED,
            applyExistAssignments(request, now),
            createNewBoard(request, now)
        );
    }

    private ExistingAssignments applyExistAssignments(
        FormationPlanRequestDto request,
        LocalDateTime now) {
        int appliedCount = 0;
        int staleCount = 0;
        for (List<ExistingBoardAssignment> assignmentChunk : chunk(
            request.existingBoardAssignments(),
            Comparator.comparing(ExistingBoardAssignment::demandBoardId))) {
            int chunkDemandTotal = assignmentChunk.stream()
                .mapToInt(a -> a.demandIds().size())
                .sum();
            try {
                self.applyExistingAssignmentChunk(assignmentChunk, now);
                appliedCount += chunkDemandTotal;
            } catch (StaleFormationItemException e) {
                staleCount += chunkDemandTotal;
                log.warn("Existing assignment chunk stale: assignments={}", assignmentChunk);
            } catch (DataAccessException e) {
                staleCount += chunkDemandTotal;
                log.error("Existing assignment chunk failed with data exception: assignments={}",
                    assignmentChunk, e);
            } catch (RuntimeException e) {
                // 심각한 오류입니다. 이후 알림이 추가될 시 이 부분에 log 알림을 붙여야 합니다
                // metrix가 있다면 해당 부분에 붙이는것도 좋아 보입니다.
                staleCount += chunkDemandTotal;
                log.error(
                    "Existing assignment chunk failed with unexpected runtime exception: "
                        + "assignments={}", assignmentChunk, e);
            }
        }
        return new ExistingAssignments(appliedCount, staleCount);
    }

    private List<NewBoardResult> createNewBoard(
        FormationPlanRequestDto request, LocalDateTime now) {
        List<NewBoardResult> newBoardResults = new ArrayList<>();
        for (List<FormationPlanRequestDto.NewBoard> newBoardList : chunk(
            request.newBoards(),
            Comparator.comparing(nb -> Collections.min(nb.demandIds()))
        )) {
            try {
                newBoardResults.addAll(self.createNewBoardChunk(newBoardList, now));
            } catch (StaleFormationItemException e) {
                addRejected(newBoardResults, newBoardList);
                log.warn("New board chunk stale: {}", newBoardList);
            } catch (DataAccessException e) {
                addRejected(newBoardResults, newBoardList);
                log.error("New board chunk failed with data exception: {}", newBoardList, e);
            } catch (RuntimeException e) {
                // 심각한 오류입니다. 이후 알림이 추가될 시 이 부분에 log 알림을 붙여야 합니다
                // metrix가 있다면 해당 부분에 붙이는것도 좋아 보입니다.
                addRejected(newBoardResults, newBoardList);
                log.error("New board chunk failed with unexpected runtime exception: {}",
                    newBoardList, e);
            }
        }
        return newBoardResults;
    }

    private void addRejected(List<NewBoardResult> results, List<NewBoard> chunk) {
        for (NewBoard nb : chunk) {
            results.add(new NewBoardResult(
                nb.clientBoardKey(), null, NewBoardStatus.STALE_REJECTED));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyExistingAssignmentChunk(List<ExistingBoardAssignment> assignments,
        LocalDateTime now) {
        int[] updateCounts = demandBatchRepository.batchAssignToExistingBoard(assignments, now);
        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] != assignments.get(i).demandIds().size()) {
                throw new StaleFormationItemException();
            }
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NewBoardResult> createNewBoardChunk(
        List<FormationPlanRequestDto.NewBoard> newBoards,
        LocalDateTime now) {
        List<DemandBoard> savedList = newBoards.stream()
            .map(newBoard -> demandBoardRepository.save(newBoard.toEntity()))
            .toList();
        List<AssignToBoardArgs> args = IntStream.range(0, newBoards.size())
            .mapToObj(i -> new AssignToBoardArgs(
                savedList.get(i).getId(),
                savedList.get(i).getSaleEndAt(),
                newBoards.get(i).demandIds()))
            .toList();
        int[] updateCounts = demandBatchRepository.batchAssignToBoard(args, now);
        List<NewBoardResult> newBoardResults = new ArrayList<>();
        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] != newBoards.get(i).demandIds().size()) {
                throw new StaleFormationItemException();
            }
            newBoardResults.add(new NewBoardResult(
                newBoards.get(i).clientBoardKey(), savedList.get(i).getId(), NewBoardStatus.CREATED)
            );
        }
        return newBoardResults;
    }


    public SubstituteOfferPlanResponseDto applySubstituteOfferPlan(
        SubstituteOfferPlanRequestDto request) {
        int appliedCount = 0;
        int staleRejectedCount = 0;
        int alreadyAppliedCount = 0;
        for (List<Proposal> proposalList : chunk(
            request.proposals(),
            Comparator.comparing(Proposal::demandId)
        )) {
            try {
                SubstituteOfferChunkResult result = self.substituteOfferChunk(proposalList);
                appliedCount += result.applied();
                alreadyAppliedCount += result.alreadyApplied();
                staleRejectedCount += result.staleRejected();
            } catch (DataAccessException e) {
                staleRejectedCount += proposalList.size();
                log.error("Substitute offer chunk failed with data exception: proposals={}",
                    proposalList, e);
            } catch (RuntimeException e) {
                // 심각한 오류입니다. 이후 알림이 추가될 시 이 부분에 log 알림을 붙여야 합니다
                // metrix가 있다면 해당 부분에 붙이는것도 좋아 보입니다.
                staleRejectedCount += proposalList.size();
                log.error(
                    "Substitute offer chunk failed with unexpected runtime exception: proposals={}",
                    proposalList, e);
            }
        }
        return new SubstituteOfferPlanResponseDto(
            SubstituteOfferPlanResponseDto.Status.APPLIED,
            appliedCount,
            alreadyAppliedCount,
            staleRejectedCount
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubstituteOfferChunkResult substituteOfferChunk(
        List<Proposal> proposalList
    ) {
        int applied = 0;
        int alreadyApplied = 0;
        int staleRejected = 0;
        for (Proposal proposal : proposalList) {
            try {
                substituteOffer(
                    proposal.demandId(),
                    proposal.demandBoardId(),
                    proposal.expectedOriginalCatalogId(),
                    proposal.substituteCatalogId()
                );
                applied++;
            } catch (BusinessException e) {
                switch (e.getErrorCode()) {
                    case DEMAND_SUBSTITUTE_ALREADY_APPLIED -> {
                        // 이미 적용된 상태는 no-op success로 취급, chunk 롤백 없이 계속 진행
                        alreadyApplied++;
                        log.info("Substitute offer already applied: demandId={}, boardId={}",
                            proposal.demandId(), proposal.demandBoardId());
                    }
                    case DEMAND_BOARD_NOT_FOUND, DEMAND_NOT_FOUND,
                         DEMAND_SUBSTITUTE_NOT_ELIGIBLE -> {
                        // 상태 변경 이전에 던져지는 예외이므로 다른 proposal에 영향 없음
                        staleRejected++;
                        log.warn("Substitute offer stale: demandId={}, boardId={}, code={}",
                            proposal.demandId(), proposal.demandBoardId(), e.getErrorCode());
                    }
                    default -> throw e;
                }
            }
        }
        return new SubstituteOfferChunkResult(applied, alreadyApplied, staleRejected);
    }

    public record SubstituteOfferChunkResult(int applied, int alreadyApplied, int staleRejected) {

    }

    private void substituteOffer(
        Long demandId,
        Long demandBoardId,
        Long expectedOriginalCatalogId,
        Long substituteCatalogId) {
        if (!demandBoardRepository.existsByIdAndStatusAndCatalogId(
            demandBoardId, DemandBoardStatus.GB_GATHERING, substituteCatalogId)) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND);
        }
        Demand demand = demandRepository.findByIdAndSubstitutableForUpdate(demandId).orElseThrow(
            () -> new BusinessException(ErrorCode.DEMAND_NOT_FOUND)
        );
        if (!Objects.equals(demand.getCatalogId(), expectedOriginalCatalogId)) {
            throw new BusinessException(ErrorCode.DEMAND_SUBSTITUTE_NOT_ELIGIBLE);
        }
        if (demand.getStatus() == DemandStatus.SUBSTITUTE_OFFERED
            && Objects.equals(demand.getDemandBoardId(), demandBoardId)) {
            throw new BusinessException(ErrorCode.DEMAND_SUBSTITUTE_ALREADY_APPLIED);
        }
        if (demand.getDemandBoardId() != null
            || demand.getStatus() != DemandStatus.UNASSIGNED) {
            throw new BusinessException(ErrorCode.DEMAND_SUBSTITUTE_NOT_ELIGIBLE);
        }
        demand.substituteOffer(demandBoardId);
    }

    public AwardingResultResponseDto applyAwardingResult(AwardingResultRequestDto request) {
        int appliedCount = 0;
        int staleRejectedCount = 0;
        for (List<BoardResult> boardResultList : chunk(
            request.results(),
            Comparator.comparing(BoardResult::boardId)
        )) {
            try {
                AwardingChunkResult result = self.awardChunk(boardResultList);
                appliedCount += result.applied();
                staleRejectedCount += result.staleRejected();
            } catch (DataAccessException e) {
                staleRejectedCount += boardResultList.size();
                log.error("Awarding chunk failed with data exception: boardResults={}",
                    boardResultList, e);
            } catch (RuntimeException e) {
                // 심각한 오류입니다. 이후 알림이 추가될 시 이 부분에 log 알림을 붙여야 합니다
                // metrix가 있다면 해당 부분에 붙이는것도 좋아 보입니다.
                staleRejectedCount += boardResultList.size();
                log.error(
                    "Awarding chunk failed with unexpected runtime exception: boardResults={}",
                    boardResultList, e);
            }
        }
        log.info("Awarding applied: total={}, applied={}, stale={}",
            appliedCount + staleRejectedCount, appliedCount, staleRejectedCount);
        return new AwardingResultResponseDto(
            AwardingResultResponseDto.Status.APPLIED,
            appliedCount,
            staleRejectedCount
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AwardingChunkResult awardChunk(List<BoardResult> boardResults) {
        int applied = 0;
        int staleRejected = 0;
        for (BoardResult br : boardResults) {
            try {
                award(br);
                applied++;
            } catch (BusinessException e) {
                switch (e.getErrorCode()) {
                    case DEMAND_BOARD_NOT_FOUND -> {
                        // 상태 변경 이전에 던져지는 예외이므로 다른 boardResult에 영향 없음
                        staleRejected++;
                        log.warn("Awarding stale: boardId={}, code={}, message={}",
                            br.boardId(), e.getErrorCode(), e.getMessage());
                    }
                    default -> throw e;
                }
            }
        }
        return new AwardingChunkResult(applied, staleRejected);
    }

    public record AwardingChunkResult(int applied, int staleRejected) {

    }

    private void award(BoardResult boardResult) {
        LocalDateTime now = LocalDateTime.now();

        Optional<Long> winnerId = Optional.empty();
        List<Long> loserIds = new ArrayList<>();
        for (Evaluation evaluation : nullSafe(boardResult.evaluations())) {
            if (Boolean.TRUE.equals(evaluation.isAwarded())) {
                winnerId = Optional.of(evaluation.productId());
            } else {
                loserIds.add(evaluation.productId());
            }
        }
        transactionBoardAndDemands(winnerId.isEmpty(), boardResult, now);
        transitionProducts(winnerId, loserIds, boardResult, now);
        productAwardEvaluationRepository.saveAll(fromBoardResult(boardResult));
        winnerId.ifPresent(groupBuyService::createGroupBuy);
    }

    private void transactionBoardAndDemands(boolean isUnawarded, BoardResult boardResult,
        LocalDateTime now) {
        int demandBoardMarked = demandBoardRepository.markAwarded(
            boardResult.boardId(),
            DemandBoardStatus.GB_AWARDING,
            isUnawarded ?
                DemandBoardStatus.GB_CANCELED :
                DemandBoardStatus.GB_ACTION_REQUIRED,
            boardResult.judgedAt()
        );
        if (demandBoardMarked == 0) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND);
        }
        int demandMarked = demandRepository.transitionStatusBulkByBoardIds(
            List.of(boardResult.boardId()),
            DemandStatus.ASSIGNED,
            isUnawarded ?
                DemandStatus.FAILED :
                DemandStatus.PAYMENT_PENDING,
            now
        );
        if (demandMarked == 0) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_NO_PARTICIPANT);
        }
    }

    private void transitionProducts(
        Optional<Long> winnerId,
        List<Long> loserIds,
        BoardResult boardResult,
        LocalDateTime now
    ) {
        int winnerUpdated = winnerId.map(id ->
            productRepository.transitionStatusForBoard(
                id, boardResult.boardId(),
                ProductStatus.AWARDING, ProductStatus.AWARDED, now)).orElse(0);
        int loserUpdated = loserIds.isEmpty() ? 0
            : productRepository.transitionStatusBulkForBoard(
                loserIds, boardResult.boardId(),
                ProductStatus.AWARDING, ProductStatus.LOST, now);

        if (winnerUpdated + loserUpdated != boardResult.evaluations().size()) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_AWARDING_INCONSISTENT);
        }
    }

    private List<ProductAwardEvaluation> fromBoardResult(BoardResult boardResult) {
        return boardResult.evaluations()
            .stream().map(
                evaluation -> ProductAwardEvaluation.builder()
                    .isAwarded(evaluation.isAwarded())
                    .demandBoardId(boardResult.boardId())
                    .score(evaluation.score())
                    .reason(evaluation.reason())
                    .productId(evaluation.productId())
                    .judgedAt(boardResult.judgedAt())
                    .build()
            ).toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <T> List<List<T>> chunk(List<T> list) {
        return chunk(list, null);
    }

    private static <T> List<List<T>> chunk(
        List<T> list, Comparator<T> comparator) {
        List<T> safe = nullSafe(list);
        if (comparator != null) {
            safe = safe.stream().sorted(comparator).toList();
        }
        final List<T> ordered = safe;
        return IntStream.range(0,
                (ordered.size() + FORMATION_PLAN_CHUNK_SIZE - 1) / FORMATION_PLAN_CHUNK_SIZE)
            .mapToObj(i -> ordered.subList(
                i * FORMATION_PLAN_CHUNK_SIZE,
                Math.min((i + 1) * FORMATION_PLAN_CHUNK_SIZE, ordered.size())))
            .toList();
    }
}
