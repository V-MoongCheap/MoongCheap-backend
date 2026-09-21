package com.moongcheap_backend.demand.application.demand;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.util.TimeUtils;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.domain.rejectHistory.RejectHistory;
import com.moongcheap_backend.demand.infrastructure.demand.DemandQueryRepository;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.demand.infrastructure.rejectHistory.RejectHistoryRepository;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandCreateRequestDto;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandListDto;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalogStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemandService {

    private final DemandRepository demandRepository;
    private final DemandQueryRepository demandQueryRepository;
    private final ProductCatalogRespository productCatalogRespository;
    private final DemandBoardRepository demandBoardRepository;
    private final RejectHistoryRepository rejectHistoryRepository;
    private final BrandPayMethodRepository brandPayMethodRepository;
    private final ProductRepository productRepository;

    private static final Set<DemandStatus> PRODUCT_ATTACHED_STATUSES = Set.of(
        DemandStatus.PAYMENT_PENDING,
        DemandStatus.CLOSED
    );

    private static final List<ProductStatus> AWARDED_PRODUCT_STATUSES = List.of(
        ProductStatus.AWARDED,
        ProductStatus.ON_SALE,
        ProductStatus.SOLD_OUT
    );

    @Transactional
    public Long create(DemandCreateRequestDto request, Long memberId) {
        // catalog 삭제의 경우 매우 드물게 일어난다는 가정 하에 lock 제약을 걸지 않음
        boolean hasCatalog = productCatalogRespository
            .existsByIdAndStatus(request.catalogId(), ProductCatalogStatus.ACTIVE);
        if (!hasCatalog) {
            throw new BusinessException(ErrorCode.PRODUCT_CATALOG_NOT_FOUND);
        }
        if (!brandPayMethodRepository
            .existsByIdAndMemberIdAndStatus(
                request.payMethodId(), memberId, PaymentsMethodStatus.ACTIVE
            )) {
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_NOT_FOUND);
        }

        Demand demand = Demand.builder()
            .memberId(memberId)
            .catalogId(request.catalogId())
            .payMethodId(request.payMethodId())
            .desiredPriceMin(request.desiredPriceMin())
            .desiredPriceMax(request.desiredPriceMax())
            .desireEndAt(TimeUtils.ceilToFiveMinuteMark(LocalDateTime.now().plusDays(2)))
            .quantity(request.quantity())
            .extraRequirement(request.extraRequirement())
            .isSubstitutable(request.isSubstitutable())
            .build();
        try {
            return demandRepository.save(demand).getId();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DEMAND_ALREADY_EXISTS);
        }

    }

    private static final List<DemandStatus> ACTIVE_STATUSES = List.of(
        DemandStatus.UNASSIGNED,
        DemandStatus.SUBSTITUTE_OFFERED,
        DemandStatus.ASSIGNED,
        DemandStatus.PAYMENT_PENDING
    );

    private static final Set<DemandStatus> CANCELABLE_STATUSES = Set.of(
        DemandStatus.UNASSIGNED,
        DemandStatus.SUBSTITUTE_OFFERED,
        DemandStatus.ASSIGNED
    );

    @Transactional(readOnly = true)
    public DemandListDto.DemandItemDto get(Long memberId, Long demandId) {
        DemandListDto.DemandItemDto item = demandQueryRepository
            .findDemandItemByIdAndMemberId(demandId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_NOT_FOUND));
        return attachProducts(List.of(item)).get(0);
    }

    @Transactional(readOnly = true)
    public DemandListDto list(Long memberId, List<DemandStatus> statuses, Pageable pageable) {
        Pageable fetchPageable = PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize() + 1, pageable.getSort());
        List<DemandListDto.DemandItemDto> items =
            demandQueryRepository.findDemandItemsByMemberId(memberId,
                statuses != null && !statuses.isEmpty() ? statuses : ACTIVE_STATUSES,
                fetchPageable);
        return DemandListDto.of(attachProducts(items), pageable);
    }

    private List<DemandListDto.DemandItemDto> attachProducts(
        List<DemandListDto.DemandItemDto> items) {
        List<Long> boardIds = items.stream()
            .filter(i -> PRODUCT_ATTACHED_STATUSES.contains(i.status()))
            .filter(i -> i.demandBoard() != null)
            .map(i -> i.demandBoard().id())
            .distinct()
            .toList();
        if (boardIds.isEmpty()) {
            return items;
        }
        Map<Long, DemandListDto.ProductDto> productByBoardId = productRepository
            .findAwardedIdAndUnitPriceByBoardIds(boardIds, AWARDED_PRODUCT_STATUSES).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> new DemandListDto.ProductDto((Long) row[1], (Integer) row[2])
            ));
        return items.stream()
            .map(i -> {
                if (!PRODUCT_ATTACHED_STATUSES.contains(i.status())
                    || i.demandBoard() == null) {
                    return i;
                }
                DemandListDto.ProductDto product = productByBoardId.get(i.demandBoard().id());
                return product == null ? i : i.withProduct(product);
            })
            .toList();
    }

    /**
     * DEMAND STATUS가 ASSIGNED, PAYMENT_PENDING 일 때에만 DEMANDBOARD의 참여자 수 감소 그 외에 CANCEL은 감소 X
     */
    @Transactional
    public void cancel(Long memberId, Long demandId) {
        Demand demand = demandRepository.findByIdForUpdate(demandId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_NOT_FOUND));
        if (!demand.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.DEMAND_FORBIDDEN);
        }
        if (!CANCELABLE_STATUSES.contains(demand.getStatus())) {
            throw new BusinessException(ErrorCode.DEMAND_CANCEL_NOT_ALLOWED);
        }
        boolean shouldDecrement = demand.getDemandBoardId() != null
            && demand.getStatus() == DemandStatus.ASSIGNED;
        demand.cancel();
        if (shouldDecrement) {
            demandBoardRepository.decrementParticipantCount(demand.getDemandBoardId());
        }
    }

    @Transactional
    public void acceptOffer(Long memberId, Long demandId) {
        Demand demand = demandRepository.findByIdAndStatusForUpdate(
                demandId,
                memberId,
                DemandStatus.SUBSTITUTE_OFFERED)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_NOT_FOUND));
        if (demand.getDesireEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DEMAND_DESIRE_EXPIRED);
        }
        if (demand.getDemandBoardId() == null) {
            throw new BusinessException(ErrorCode.DEMAND_ACCEPT_NOT_ALLOWED);
        }
        int updated = demandBoardRepository.increaseParticipantCountIfActive(
            demand.getDemandBoardId(), DemandBoardStatus.GB_GATHERING);
        if (updated == 0) {
            demand.rejectOffer();
        } else {
            demand.acceptOffer();
        }
    }

    @Transactional
    public void rejectOffer(Long memberId, Long demandId) {
        Demand demand = demandRepository.findByIdAndStatusForUpdate(
                demandId,
                memberId,
                DemandStatus.SUBSTITUTE_OFFERED)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEMAND_NOT_FOUND));
        if (demand.getDesireEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DEMAND_DESIRE_EXPIRED);
        }
        if (demand.getDemandBoardId() == null) {
            throw new BusinessException(ErrorCode.DEMAND_ACCEPT_NOT_ALLOWED);
        }
        rejectHistoryRepository.save(RejectHistory.of(demand.getId(), demand.getDemandBoardId()));
        demand.rejectOffer();
    }

}
