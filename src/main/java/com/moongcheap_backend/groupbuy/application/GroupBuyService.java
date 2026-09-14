package com.moongcheap_backend.groupbuy.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyDetailResponse;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyDetailResponse.ProductInfo;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyListResponse;
import com.moongcheap_backend.member.application.SellerPublicService;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.product.application.product.ProductPublicService;
import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private static final long JUDGMENT_DELAY_MINUTES = 5;
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    //repo
    private final GroupBuyRepository groupBuyRepository;
    private final OutboxEventRepository outboxEventRepository;

    //service
    private final ProductPublicService productPublicService;
    private final SellerPublicService sellerPublicService;

    //공동구매 목록 조회
    @Transactional(readOnly = true)
    public Page<GroupBuyListResponse> getAll(Pageable pageable) {
        return groupBuyRepository.findAllByStatus(GroupBuyStatus.OPEN, pageable)
            .map(this::toListResponse);
    }

    //공동구매 상세 조회
    @Transactional(readOnly = true)
    public GroupBuyDetailResponse getById(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findDetailById(groupBuyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUPBUY_NOT_FOUND));

        return toDetailResponse(groupBuy);
    }

    //공동구매 생성
    @Transactional
    public Void createGroupBuy(Long productId) {
        // 만료시간 이전의 조건에 맞는 상품데이터
        Product product = productPublicService.
            getByIdAndStatus(productId, ProductStatus.AWARDED);

        GroupBuy groupBuy = new GroupBuy(
            sellerPublicService.getByIdAndStatus(product.getSellerId(), SellerStatus.APPROVED),
            product,
            productPublicService.getCatalogNameById(product.getCatalogId()),
            product.getMinParticipantCount(),
            0,
            product.getSaleEndAt(),
            GroupBuyStatus.OPEN
        );

        product.startSale();

        // 공동구매와 Outbox를 같은 트랜잭션으로 저장해 Redis 예약 유실을 방지한다.
        GroupBuy savedGroupBuy = groupBuyRepository.save(groupBuy);
        outboxEventRepository.save(OutboxEvent.groupBuyJudgmentScheduled(
            savedGroupBuy.getId(),
            savedGroupBuy.getGroupBuyEndAt().plusMinutes(JUDGMENT_DELAY_MINUTES),
            LocalDateTime.now(ZONE_SEOUL)
        ));

        return null;
    }

    private GroupBuyListResponse toListResponse(GroupBuy groupBuy) {
        return new GroupBuyListResponse(
            groupBuy.getTitle(),
            groupBuy.getProduct().getThumbnailUrl(),
            groupBuy.getProduct().getUnitPrice()
        );
    }

    private GroupBuyDetailResponse toDetailResponse(GroupBuy groupBuy) {
        Product product = groupBuy.getProduct();
        return new GroupBuyDetailResponse(
            groupBuy.getTitle(),
            product.getThumbnailUrl(),
            product.getUnitPrice(),
            groupBuy.getTargetCount(),
            new ProductInfo(
                product.getShippingFee(),
                product.getDeliveryDate(),
                product.getSaleEndAt(),
                product.getTotalQuantity()
            )
        );
    }
}
