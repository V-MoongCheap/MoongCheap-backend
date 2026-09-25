package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로컬 자동결제 검증에 필요한 최소 주문 aggregate를 생성한다. */
@Profile({"local", "dev"})
@Service
@RequiredArgsConstructor
public class DevBrandPayTestOrderService {

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final DemandBoardRepository demandBoardRepository;
    private final ProductRepository productRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final DemandRepository demandRepository;
    private final OrdersRepository ordersRepository;
    private final BrandPayMethodRepository brandPayMethodRepository;

    /**
     * 선택한 활성 결제수단을 사용하는 PAYMENT_PENDING 주문을 만든다.
     * 실제 자동결제 전제조건을 만족하도록 테스트 전용 판매자·상품·공동구매·수요도
     * 같은 트랜잭션에서 생성하며 local/dev 프로필 밖에서는 서비스가 존재하지 않는다.
     */
    @Transactional
    public TestOrderResult create(Long memberId, Long paymentMethodId, int amount,
        String orderName) {
        if (amount < 100 || orderName == null || orderName.isBlank()
            || orderName.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        var paymentMethod = brandPayMethodRepository
            .findByIdAndMemberId(paymentMethodId, memberId)
            .filter(method -> method.getStatus() == PaymentsMethodStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_PAY_METHOD_NOT_FOUND));

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Seller seller = sellerRepository.findByMemberIdAndDeletedAtIsNull(memberId)
            .orElseGet(() -> createSeller(memberId, suffix));

        ProductCatalog catalog = productCatalogRepository.save(ProductCatalog.builder()
            .name("BrandPay test " + suffix)
            .specSummary("자동결제 로컬 테스트 상품")
            .listPrice(amount)
            .thumbnailUrl("https://example.com/brandpay-test.png")
            .description("local/dev 프로필에서 생성한 자동결제 테스트 데이터")
            .build());

        LocalDateTime now = LocalDateTime.now();
        DemandBoard board = demandBoardRepository.save(DemandBoard.builder()
            .catalogId(catalog.getId())
            .priceMin(amount)
            .priceMax(amount)
            .saleEndAt(now.plusDays(1))
            .participantCount(1)
            .status(DemandBoardStatus.GB_CLOSED)
            .build());

        Product product = productRepository.save(Product.builder()
            .catalogId(catalog.getId())
            .demandBoardId(board.getId())
            .sellerId(seller.getId())
            .thumbnailUrl("https://example.com/brandpay-test.png")
            .unitPrice(amount)
            .shippingFee(0)
            .deliveryDate(LocalDate.now().plusDays(7))
            .saleEndAt(now)
            .totalQuantity(1)
            .minParticipantCount(1)
            .minQuantity(1)
            .maxQuantityPerMember(1)
            .description("BrandPay 자동결제 테스트 상품")
            .returnPolicy("테스트 데이터")
            .build());

        GroupBuy groupBuy = groupBuyRepository.save(new GroupBuy(
            seller, product, orderName, 1, 1, now, GroupBuyStatus.RECRUITMENT_COMPLETED));

        Demand demand = demandRepository.save(Demand.boardJoinBuilder()
            .memberId(memberId)
            .catalogId(catalog.getId())
            .demandBoardId(board.getId())
            .payMethodId(paymentMethodId)
            .desiredPriceMin(amount)
            .desiredPriceMax(amount)
            .desireEndAt(now)
            .quantity(1)
            .isSubstitutable(false)
            .extraRequirement("BrandPay 자동결제 테스트")
            .build());

        String orderNo = "bp-test-" + suffix;
        Orders order = ordersRepository.save(Orders.create(
            orderNo,
            demand.getId(),
            member.getId(),
            paymentMethod,
            groupBuy,
            product.getId(),
            orderName,
            "https://example.com/brandpay-test.png",
            1,
            amount,
            0,
            seller.getId(),
            seller.getBusinessName()
        ));

        return new TestOrderResult(order.getId(), order.getOrderNo(),
            order.getOrderStatus().name(), order.getTotalAmount(), paymentMethod.getId());
    }

    private Seller createSeller(Long memberId, String suffix) {
        String hash = suffix.repeat(6).substring(0, 64);
        Seller seller = Seller.builder()
            .memberId(memberId)
            .businessName("BrandPay Test Seller")
            .businessNumber("test-business-" + suffix)
            .businessNumberHash(hash)
            .mailOrderRegistrationNumber("test-" + suffix)
            .ownerName("테스트 판매자")
            .phoneNumber("01000000000")
            .build();
        seller.approve();
        return sellerRepository.save(seller);
    }

    public record TestOrderResult(
        Long orderId,
        String orderNo,
        String status,
        Integer amount,
        Long paymentMethodId
    ) {
    }
}
