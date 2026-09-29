package com.moongcheap_backend.order.application;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.application.demand.OrderDemandService;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.groupbuy.application.GroupBuyPublicService;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.member.application.OrderMemberInfoService;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrderStatusCount;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.order.presentation.OrderController.OrderListTab;
import com.moongcheap_backend.order.presentation.dto.OrderDetailResponse;
import com.moongcheap_backend.order.presentation.dto.OrderDetailResponse.GroupBuyInfo;
import com.moongcheap_backend.order.presentation.dto.OrderDetailResponse.PaymentInfo;
import com.moongcheap_backend.order.presentation.dto.OrderDetailResponse.ProductInfo;
import com.moongcheap_backend.order.presentation.dto.OrderDetailResponse.ShippingInfo;
import com.moongcheap_backend.order.presentation.dto.OrderListResponse;
import com.moongcheap_backend.order.presentation.dto.OrderShippingAddressRequest;
import com.moongcheap_backend.order.presentation.dto.OrderSummaryResponse;
import com.moongcheap_backend.payments.application.PaymentPublicService;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.presentation.dto.OrderPaymentInfo;
import com.moongcheap_backend.product.domain.product.Product;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private static final int ORDER_BATCH_SIZE = 20;
    private static final Set<OrderStatus> SUMMARY_STATUSES = Set.of(
        OrderStatus.PAYMENT_COMPLETED,
        OrderStatus.PREPARING_SHIPMENT,
        OrderStatus.SHIPPED,
        OrderStatus.DELIVERED
    );

    //repo
    private final OrdersRepository ordersRepository;

    //service
    private final OrderMemberInfoService orderMemberInfoService;
    private final PaymentPublicService orderPaymentInfoService;
    private final EncryptionService encryptionService;
    private final GroupBuyPublicService groupBuyPublicService;
    private final OrderDemandService orderDemandService;
    private final EntityManager entityManager;

    //자동주문
    @Transactional
    public Void autoCreateOrder(Long groupBuyId) {
        //groupbuy호출 및 검증하고 seller, product join해서 가져오기
        GroupBuy groupBuy = groupBuyPublicService.getOrderSource(groupBuyId);
        Seller seller = groupBuy.getSeller();
        Product product = groupBuy.getProduct();

        //seller, product 검증
        if (seller.getDeletedAt() != null) {
            throw orderCreationRejected(groupBuyId, seller, product,
                ErrorCode.SELLER_NOT_FOUND, "SELLER_DELETED");
        }
        if (!seller.isSellable()) {
            throw orderCreationRejected(groupBuyId, seller, product,
                ErrorCode.SELLER_NOT_APPROVED, "SELLER_NOT_SELLABLE");
        }
        if (!product.isOnSale()) {
            throw orderCreationRejected(groupBuyId, seller, product,
                ErrorCode.PRODUCT_NOT_ORDERABLE, "PRODUCT_NOT_ON_SALE");
        }
        if (!product.getSellerId().equals(seller.getId())) {
            throw orderCreationRejected(groupBuyId, seller, product,
                ErrorCode.PRODUCT_NOT_ORDERABLE, "PRODUCT_SELLER_MISMATCH");
        }
        if (product.getUnitPrice() == null) {
            throw orderCreationRejected(groupBuyId, seller, product,
                ErrorCode.PRODUCT_NOT_ORDERABLE, "PRODUCT_UNIT_PRICE_MISSING");
        }
        if (product.getShippingFee() == null) {
            throw orderCreationRejected(groupBuyId, seller, product,
                ErrorCode.PRODUCT_NOT_ORDERABLE, "PRODUCT_SHIPPING_FEE_MISSING");
        }

        //demand_board_id로 demand에서 대상 추출
        List<Demand> demands = orderDemandService.getPaymentPendingForOrder(
            product.getDemandBoardId()
        );

        // Stream은 같은 메시지를 다시 전달할 수 있으므로 이미 주문이 된 수요는 제외한다.
        Set<Long> existingDemandIds = demands.isEmpty()
            ? Set.of()
            : new HashSet<>(ordersRepository.findExistingDemandIds(
                demands.stream().map(Demand::getId).toList()
            ));

        Set<Long> demandMemberIds = demands.stream()
            .map(Demand::getMemberId)
            .collect(Collectors.toSet());
        Set<Long> activeMemberIds = orderMemberInfoService.getActiveMemberIds(demandMemberIds);

        //demand리스트로 order리스트 생성
        List<Orders> orders = demands.stream()
            .filter(demand -> !existingDemandIds.contains(demand.getId()))
            // 탈퇴하지 않아 deletedAt이 null인 회원의 수요만 주문으로 생성한다.
            .filter(demand -> activeMemberIds.contains(demand.getMemberId()))
            .filter(demand -> product.getUnitPrice() <= demand.getDesiredPriceMax())
            .map(demand -> Orders.create(
                createOrderNo(),
                demand.getId(),
                demand.getMemberId(),
                getBrandPayMethodReference(demand.getPayMethodId()),
                groupBuy,
                product.getId(),
                groupBuy.getTitle(),
                product.getThumbnailUrl(),
                demand.getQuantity(),
                product.getUnitPrice(),
                product.getShippingFee(),
                seller.getId(),
                seller.getBusinessName()
            ))
            .toList();

        for (int fromIndex = 0; fromIndex < orders.size(); fromIndex += ORDER_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + ORDER_BATCH_SIZE, orders.size());
            ordersRepository.saveAll(orders.subList(fromIndex, toIndex));
        }

        // 실제 주문으로 생성된 수요만 공동구매 참여 인원에 반영한다.
        groupBuy.increaseParticipantCount(orders.size());
        return null;
    }

    // 외부 오류 코드는 유지하면서 운영 로그에서는 실제 검증 실패 조건을 구분한다.
    private BusinessException orderCreationRejected(Long groupBuyId, Seller seller,
        Product product, ErrorCode errorCode, String reason) {
        log.warn("Order creation rejected: groupBuyId={}, sellerId={}, productId={}, "
                + "productSellerId={}, sellerStatus={}, productStatus={}, errorCode={}, reason={}",
            groupBuyId, seller.getId(), product.getId(), product.getSellerId(),
            seller.getStatus(), product.getStatus(), errorCode, reason);
        return new BusinessException(errorCode);
    }

    private BrandPayMethod getBrandPayMethodReference(Long payMethodId) {
        return payMethodId == null
            ? null
            : entityManager.getReference(BrandPayMethod.class, payMethodId);
    }

    private String createOrderNo() {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "ORD-" + timestamp + "-" + uuid;
    }

    //주문목록조회
    @Transactional(readOnly = true)
    public Page<OrderListResponse> viewOrderList(Long memberId, OrderListTab orderListTab,
        Pageable pageable) {
        orderMemberInfoService.validateActiveMember(memberId);

        Page<Orders> orders = switch (orderListTab) {
            case ALL -> ordersRepository.findAllByMemberId(memberId, pageable);
            case IN_PROGRESS -> ordersRepository.findAllByMemberIdAndOrderStatusIn(
                memberId,
                Set.of(
                    OrderStatus.PAYMENT_COMPLETED,
                    OrderStatus.PREPARING_SHIPMENT,
                    OrderStatus.SHIPPED
                ),
                pageable
            );
            case DELIVERED -> ordersRepository.findAllByMemberIdAndOrderStatusIn(
                memberId, Set.of(OrderStatus.DELIVERED), pageable
            );
            case COMPLETED -> ordersRepository.findAllByMemberIdAndOrderStatusIn(
                memberId, Set.of(OrderStatus.COMPLEDED), pageable
            );
        };

        return orders.map(this::toOrderListResponse);
    }

    @Transactional(readOnly = true)
    public OrderSummaryResponse getSummary(Long memberId) {
        orderMemberInfoService.validateActiveMember(memberId);

        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatusCount result
            : ordersRepository.countByMemberIdAndOrderStatusIn(memberId, SUMMARY_STATUSES)) {
            counts.put(result.getOrderStatus(), result.getCount());
        }

        return new OrderSummaryResponse(
            counts.getOrDefault(OrderStatus.PAYMENT_COMPLETED, 0L),
            counts.getOrDefault(OrderStatus.PREPARING_SHIPMENT, 0L),
            counts.getOrDefault(OrderStatus.SHIPPED, 0L),
            counts.getOrDefault(OrderStatus.DELIVERED, 0L)
        );
    }

    //주문상세조회
    @Transactional(readOnly = true)
    public OrderDetailResponse viewOrderDetail(Long memberId, String orderNo) {
        orderMemberInfoService.validateActiveMember(memberId);

        Orders order = ordersRepository.findDetailByOrderNoAndMemberId(orderNo, memberId).
            orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        GroupBuy groupBuy = order.getGroupBuy();
        OrderPaymentInfo paymentInfo = orderPaymentInfoService.getForOrder(order);

        return new OrderDetailResponse(
            order.getCreatedAt().toLocalDate(),
            orderNo,
            new ProductInfo(
                order.getBusinessName(),
                "택배",
                order.getDeliveryFee(),
                order.getOrderStatus(),
                order.getImageUrl(),
                order.getProductName(),
                order.getSum(),
                order.getPrice(),
                new GroupBuyInfo(
                    groupBuy.getId(),
                    groupBuy.getTitle()
                )
            ),
            new ShippingInfo(
                order.getShippingName(),
                encryptionService.maskPhoneNumber(
                    encryptionService.decrypt(order.getPhoneNumber())),
                combineAddress(order.getAddress(), order.getAddressDetail())
            ),
            new PaymentInfo(
                paymentInfo.productAmount(),
                paymentInfo.deliveryFee(),
                paymentInfo.totalPaymentAmount(),
                paymentInfo.paymentMethod()
            )
        );
    }

    //주문취소
    @Transactional
    public void orderCancel(Long memberId, String orderNo) {
        orderMemberInfoService.validateActiveMember(memberId);

        Orders order = findOrderForUpdate(orderNo, memberId);

        if (order.getOrderStatus() == OrderStatus.CANCELED) {
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        //결제된것 환불

        groupBuyPublicService.decreaseParticipantCount(order.getGroupBuy().getId());
        order.setOrderStatus(OrderStatus.CANCELED);
    }

    //배송지 입력
    @Transactional
    public Void updateShippingAddress(Long memberId,
        String orderNo,
        OrderShippingAddressRequest request
    ) {
        orderMemberInfoService.validateActiveMember(memberId);

        Orders order = findOrderForUpdate(orderNo, memberId);

        if (order.getOrderStatus() != OrderStatus.PAYMENT_COMPLETED) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_SHIPPING);
        }

        String phoneDigits = request.phoneNumber().replaceAll("[^0-9]", "");

        order.updateShipping(
            request.shippingName(),
            encryptionService.encrypt(phoneDigits),
            request.zipcode(),
            request.address(),
            request.addressDetail(),
            request.shippingMemo()
        );

        return null;
    }



    private String combineAddress(String address, String addressDetail) {
        String baseAddress = address == null || address.isBlank() ? null : address;
        String detailAddress = addressDetail == null || addressDetail.isBlank()
            ? null
            : addressDetail;

        if (baseAddress == null) {
            return detailAddress;
        }
        if (detailAddress == null) {
            return baseAddress;
        }

        return baseAddress + " " + detailAddress;
    }

    private OrderListResponse toOrderListResponse(Orders order) {
        return new OrderListResponse(
            order.getCreatedAt().toLocalDate(),
            order.getOrderNo(),
            order.getBusinessName(),
            order.getOrderStatus(),
            order.getImageUrl(),
            order.getProductName(),
            order.getSum(),
            order.getTotalAmount()
        );
    }

    //주문 검색(수정용)
    private Orders findOrderForUpdate(String orderNo, Long memberId) {
        Orders order = ordersRepository.
            findByOrderNoAndMemberId(orderNo, memberId).
            orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        return order;
    }
}
