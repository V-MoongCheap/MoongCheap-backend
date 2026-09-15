package com.moongcheap_backend.demand.application.demandBoard;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AuctionResultDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultRequestDto.BoardResult;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.AwardingResultResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.CatalogDemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardJoinRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardListDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.DemandBoardSummaryDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanRequestDto.ExistingBoardAssignment;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.ExistingAssignments;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.NewBoardResult;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.NewBoardStatus;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.FormationPlanResponseDto.Status;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanRequestDto.Proposal;
import com.moongcheap_backend.demand.presentation.demandBoard.dto.SubstituteOfferPlanResponseDto;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.domain.productAwardEvaluation.ProductAwardEvaluation;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productAwardEvaluation.ProductAwardEvaluationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    private final ProductAwardEvaluationRepository productAwardEvaluationRepository;
    private final ProductRepository productRepository;

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

    @Transactional
    public Long join(Long memberId, Long demandBoardId, DemandBoardJoinRequestDto request) {
        DemandBoard demandBoard = demandBoardRepository.findByIdAndStatusInForUpdate(demandBoardId,
                List.of(DemandBoardStatus.GB_GATHERING))
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_BOARD_NOT_FOUND));
        if (demandBoard.getSaleEndAt() == null
            || demandBoard.getSaleEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_CLOSED);
        }
        //TODO: payMethodId 검증 로직
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

    @Transactional
    public FormationPlanResponseDto applyFormationPlan(FormationPlanRequestDto request) {
        LocalDateTime now = LocalDateTime.now();

        int appliedCount = 0;
        int staleCount = 0;
        for (ExistingBoardAssignment assignment : nullSafe(request.existingBoardAssignments())) {
            int size = assignment.demandIds().size();
            try {
                self.applyExistingAssignment(assignment, now);
                appliedCount += size;
            } catch (StaleFormationItemException e) {
                staleCount += size;
                log.warn("Existing assignment stale: boardId={}, demandIds={}",
                    assignment.demandBoardId(), assignment.demandIds());
            }
        }

        List<NewBoardResult> newBoardResults = new ArrayList<>();
        for (FormationPlanRequestDto.NewBoard newBoard : nullSafe(request.newBoards())) {
            try {
                Long boardId = self.createNewBoard(newBoard, now);
                newBoardResults.add(
                    new NewBoardResult(newBoard.clientBoardKey(), boardId, NewBoardStatus.CREATED));
            } catch (StaleFormationItemException e) {
                newBoardResults.add(
                    new NewBoardResult(newBoard.clientBoardKey(), null,
                        NewBoardStatus.STALE_REJECTED));
                log.warn("New board stale: clientBoardKey={}, demandIds={}",
                    newBoard.clientBoardKey(), newBoard.demandIds());
            }
        }

        return new FormationPlanResponseDto(
            Status.APPLIED,
            new ExistingAssignments(appliedCount, staleCount),
            newBoardResults
        );
    }

    @Transactional(propagation = Propagation.NESTED)
    public void applyExistingAssignment(ExistingBoardAssignment assignment, LocalDateTime now) {
        List<Long> demandIds = assignment.demandIds();
        int updated = demandRepository.assignToExistingBoard(
            assignment.demandBoardId(), demandIds.size(), now, demandIds);
        if (updated != demandIds.size()) {
            throw new StaleFormationItemException();
        }
    }

    @Transactional(propagation = Propagation.NESTED)
    public Long createNewBoard(FormationPlanRequestDto.NewBoard newBoard, LocalDateTime now) {
        DemandBoard saved = demandBoardRepository.save(newBoard.toEntity());
        int updated = demandRepository.assignToBoard(
            saved.getId(), saved.getSaleEndAt(), now, newBoard.demandIds());
        if (updated != newBoard.demandIds().size()) {
            entityManager.detach(saved);
            throw new StaleFormationItemException();
        }
        return saved.getId();
    }

    @Transactional
    public SubstituteOfferPlanResponseDto applySubstituteOfferPlan(
        SubstituteOfferPlanRequestDto request) {
        int appliedCount = 0;
        int staleRejectedCount = 0;
        int alreadyAppliedCount = 0;
        for (Proposal proposal : nullSafe(request.proposals())) {
            try {
                self.substituteOffer(
                    proposal.demandId(),
                    proposal.demandBoardId(),
                    proposal.expectedOriginalCatalogId(),
                    proposal.substituteCatalogId());
                appliedCount += 1;
            } catch (BusinessException e) {
                switch (e.getErrorCode()) {
                    case DEMAND_SUBSTITUTE_ALREADY_APPLIED -> {
                        alreadyAppliedCount += 1;
                        log.info("Substitute offer already applied: demandId={}, boardId={}",
                            proposal.demandId(), proposal.demandBoardId());
                    }
                    case DEMAND_BOARD_NOT_FOUND, DEMAND_NOT_FOUND,
                         DEMAND_SUBSTITUTE_NOT_ELIGIBLE -> {
                        staleRejectedCount += 1;
                        log.warn("Substitute offer stale: demandId={}, boardId={}, code={}",
                            proposal.demandId(), proposal.demandBoardId(), e.getErrorCode());
                    }
                    default -> throw e;
                }
            }
        }
        return new SubstituteOfferPlanResponseDto(
            SubstituteOfferPlanResponseDto.Status.APPLIED,
            appliedCount,
            alreadyAppliedCount,
            staleRejectedCount
        );
    }

    @Transactional(propagation = Propagation.NESTED)
    public void substituteOffer(
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

    @Transactional
    public AwardingResultResponseDto applyAwardingResult(AwardingResultRequestDto request) {
        int appliedCount = 0;
        int staleRejectedCount = 0;
        for (BoardResult boardResult : nullSafe(request.results())) {
            try {
                self.award(boardResult);
                appliedCount += 1;
            } catch (BusinessException e) {
                switch (e.getErrorCode()) {
                    case DEMAND_BOARD_NOT_FOUND,
                         DEMAND_BOARD_AWARDING_INCONSISTENT,
                         DEMAND_NOT_FOUND -> {
                        staleRejectedCount += 1;
                        log.warn("Awarding stale: boardId={}, code={}, message={}",
                            boardResult.boardId(), e.getErrorCode(), e.getMessage());
                    }
                    default -> throw e;
                }
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

    @Transactional(propagation = Propagation.NESTED)
    public void award(BoardResult boardResult) {
        LocalDateTime now = LocalDateTime.now();

        List<Long> winnerIds = boardResult.evaluations().stream()
            .filter(e -> Boolean.TRUE.equals(e.isAwarded()))
            .map(AwardingResultRequestDto.Evaluation::productId)
            .toList();
        List<Long> loserIds = boardResult.evaluations().stream()
            .filter(e -> !Boolean.TRUE.equals(e.isAwarded()))
            .map(AwardingResultRequestDto.Evaluation::productId)
            .toList();
        boolean isUnawarded = winnerIds.isEmpty();
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
            throw new BusinessException(ErrorCode.DEMAND_NOT_FOUND);
        }
        int winnerUpdated = winnerIds.isEmpty() ? 0
            : productRepository.transitionStatusBulkForBoard(
                winnerIds, boardResult.boardId(),
                ProductStatus.AWARDING, ProductStatus.AWARDED, now);
        int loserUpdated = loserIds.isEmpty() ? 0
            : productRepository.transitionStatusBulkForBoard(
                loserIds, boardResult.boardId(),
                ProductStatus.AWARDING, ProductStatus.LOST, now);

        if (winnerUpdated + loserUpdated != boardResult.evaluations().size()) {
            throw new BusinessException(ErrorCode.DEMAND_BOARD_AWARDING_INCONSISTENT);
        }

        List<ProductAwardEvaluation> productAwardEvaluation = boardResult.evaluations()
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
        productAwardEvaluationRepository.saveAll(productAwardEvaluation);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
