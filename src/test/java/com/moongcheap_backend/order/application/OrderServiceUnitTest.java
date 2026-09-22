package com.moongcheap_backend.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
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
import com.moongcheap_backend.order.presentation.dto.OrderListResponse;
import com.moongcheap_backend.order.presentation.dto.OrderShippingAddressRequest;
import com.moongcheap_backend.order.presentation.dto.OrderSummaryResponse;
import com.moongcheap_backend.payments.application.PaymentPublicService;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.presentation.dto.OrderPaymentInfo;
import com.moongcheap_backend.product.domain.product.Product;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ORDER_NO = "ORD-TEST";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderMemberInfoService orderMemberInfoService;
    @Mock
    private PaymentPublicService orderPaymentInfoService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private GroupBuyPublicService groupBuyPublicService;
    @Mock
    private OrderDemandService orderDemandService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<List<Orders>> ordersCaptor;

    @Nested
    @DisplayName("자동 주문 정상 테스트")
    class AutoCreateOrderTest {

        private GroupBuy preparedGroupBuy;

        @Test
        void 수요_40건을_주문으로_변환해_20건씩_나누어_저장한다() {
            List<Demand> demands = createDemands(40);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository, times(2)).saveAll(ordersCaptor.capture());
            List<List<Orders>> batches = ordersCaptor.getAllValues();
            assertThat(batches).hasSize(2).allSatisfy(batch -> assertThat(batch).hasSize(20));

            List<Orders> savedOrders = batches.stream().flatMap(List::stream).toList();
            assertThat(savedOrders).hasSize(40);
            assertThat(savedOrders)
                .extracting(Orders::getMemberId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 40)
                    .mapToObj(Long::valueOf)
                    .toList());
            assertThat(savedOrders)
                .extracting(Orders::getSum)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 40).boxed().toList());
            assertThat(savedOrders)
                .extracting(Orders::getTotalAmount)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 40)
                    .map(quantity -> 10_000 * quantity)
                    .boxed()
                    .toList());
            assertThat(savedOrders)
                .extracting(Orders::getOrderNo)
                .allMatch(orderNo -> orderNo.startsWith("ORD-"))
                .doesNotHaveDuplicates();
        }

        @Test
        void 수요_20건을_주문으로_변환해_한번에_저장한다() {
            List<Demand> demands = createDemands(20);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            assertThat(ordersCaptor.getValue()).hasSize(20);
        }

        @Test
        void 수요_21건을_주문으로_변환해_20건과_1건으로_나누어_저장한다() {
            List<Demand> demands = createDemands(21);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository, times(2)).saveAll(ordersCaptor.capture());
            List<List<Orders>> batches = ordersCaptor.getAllValues();
            assertThat(batches).hasSize(2);
            assertThat(batches.get(0)).hasSize(20);
            assertThat(batches.get(1)).hasSize(1);
            assertThat(batches.stream().flatMap(List::stream)).hasSize(21);
        }

        @Test
        void 수요_19건을_주문으로_변환해_한번에_저장한다() {
            List<Demand> demands = createDemands(19);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            assertThat(ordersCaptor.getValue()).hasSize(19);
        }

        @Test
        void 주문이_생성되면_배송_정보는_null이다() {
            List<Demand> demands = createDemands(1);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            Orders createdOrder = ordersCaptor.getValue().getFirst();
            assertThat(createdOrder)
                .extracting(
                    Orders::getShippingName,
                    Orders::getPhoneNumber,
                    Orders::getZipcode,
                    Orders::getAddress,
                    Orders::getAddressDetail,
                    Orders::getShippingMemo,
                    Orders::getShippingNumber
                )
                .containsOnlyNulls();
        }

        @Test
        void 생성된_주문의_수량은_수요의_수량과_같다() {
            Demand demand = org.mockito.Mockito.mock(Demand.class);
            when(demand.getId()).thenReturn(100L);
            when(demand.getMemberId()).thenReturn(1L);
            when(demand.getQuantity()).thenReturn(7);
            when(demand.getDesiredPriceMax()).thenReturn(10_000);
            prepareOrderSource(List.of(demand));

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            Orders createdOrder = ordersCaptor.getValue().getFirst();
            assertThat(createdOrder.getSum()).isEqualTo(demand.getQuantity());
        }

        @Test
        void 생성된_주문의_단가와_배송비는_상품의_단가와_배송비와_같다() {
            List<Demand> demands = createDemands(1);
            Product product = prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            Orders createdOrder = ordersCaptor.getValue().getFirst();
            assertThat(createdOrder.getPrice()).isEqualTo(product.getUnitPrice());
            assertThat(createdOrder.getDeliveryFee()).isEqualTo(product.getShippingFee());
        }

        @Test
        void 생성된_주문의_총금액은_수요_수량과_상품_단가를_곱한_금액이다() {
            Demand demand = org.mockito.Mockito.mock(Demand.class);
            when(demand.getId()).thenReturn(100L);
            when(demand.getMemberId()).thenReturn(1L);
            when(demand.getQuantity()).thenReturn(7);
            when(demand.getDesiredPriceMax()).thenReturn(10_000);
            Product product = prepareOrderSource(List.of(demand));

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            Orders createdOrder = ordersCaptor.getValue().getFirst();
            assertThat(createdOrder.getTotalAmount())
                .isEqualTo(createdOrder.getSum() * createdOrder.getPrice())
                .isEqualTo(demand.getQuantity() * product.getUnitPrice());
        }

        @Test
        void 생성된_주문의_수요_id는_원본_수요의_id와_같다() {
            List<Demand> demands = createDemands(1);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            Orders createdOrder = ordersCaptor.getValue().getFirst();
            assertThat(createdOrder.getDemandId()).isEqualTo(demands.getFirst().getId());
        }

        @Test
        void 수요_39건을_주문으로_변환해_20건과_19건으로_나누어_저장한다() {
            List<Demand> demands = createDemands(39);
            prepareOrderSource(demands);

            orderService.autoCreateOrder(10L);

            verify(ordersRepository, times(2)).saveAll(ordersCaptor.capture());
            List<List<Orders>> batches = ordersCaptor.getAllValues();
            assertThat(batches).hasSize(2);
            assertThat(batches.get(0)).hasSize(20);
            assertThat(batches.get(1)).hasSize(19);
            assertThat(batches.stream().flatMap(List::stream)).hasSize(39);
            verify(preparedGroupBuy).increaseParticipantCount(39);
        }

        @Test
        void 이미_주문이_생성된_수요는_제외하고_새_주문만_참여_인원에_반영한다() {
            Demand existingDemand = org.mockito.Mockito.mock(Demand.class);
            when(existingDemand.getId()).thenReturn(1L);
            when(existingDemand.getMemberId()).thenReturn(1L);
            Demand newDemand = org.mockito.Mockito.mock(Demand.class);
            when(newDemand.getId()).thenReturn(2L);
            when(newDemand.getMemberId()).thenReturn(2L);
            when(newDemand.getQuantity()).thenReturn(2);
            when(newDemand.getDesiredPriceMax()).thenReturn(10_000);
            List<Demand> demands = List.of(existingDemand, newDemand);
            prepareOrderSource(demands);
            when(ordersRepository.findExistingDemandIds(List.of(1L, 2L)))
                .thenReturn(List.of(1L));

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            assertThat(ordersCaptor.getValue())
                .singleElement()
                .extracting(Orders::getDemandId)
                .isEqualTo(2L);
            verify(preparedGroupBuy).increaseParticipantCount(1);
        }

        private List<Demand> createDemands(int count) {
            return IntStream.rangeClosed(1, count)
                .mapToObj(index -> {
                    Demand demand = org.mockito.Mockito.mock(Demand.class);
                    when(demand.getId()).thenReturn((long) index);
                    when(demand.getMemberId()).thenReturn((long) index);
                    when(demand.getQuantity()).thenReturn(index);
                    when(demand.getDesiredPriceMax()).thenReturn(10_000);
                    return demand;
                })
                .toList();
        }

        private Product prepareOrderSource(List<Demand> demands) {
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            preparedGroupBuy = groupBuy;
            Seller seller = org.mockito.Mockito.mock(Seller.class);
            Product product = org.mockito.Mockito.mock(Product.class);

            when(groupBuyPublicService.getOrderSource(10L)).thenReturn(groupBuy);
            when(groupBuy.getSeller()).thenReturn(seller);
            when(groupBuy.getProduct()).thenReturn(product);
            when(groupBuy.getTitle()).thenReturn("제주 감귤 공동구매");
            when(seller.isSellable()).thenReturn(true);
            when(seller.getId()).thenReturn(20L);
            when(seller.getBusinessName()).thenReturn("문치프 농장");
            when(product.isOnSale()).thenReturn(true);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/image.jpg");
            when(product.getDemandBoardId()).thenReturn(30L);
            when(product.getId()).thenReturn(40L);
            when(product.getUnitPrice()).thenReturn(10_000);
            when(product.getShippingFee()).thenReturn(3_000);
            when(orderDemandService.getPaymentPendingForOrder(30L)).thenReturn(demands);
            Set<Long> memberIds = demands.stream()
                .map(Demand::getMemberId)
                .collect(java.util.stream.Collectors.toSet());
            when(orderMemberInfoService.getActiveMemberIds(memberIds)).thenReturn(memberIds);
            return product;
        }

        @Test
        void 결제수단이_있는_수요와_없는_수요의_주문을_생성한다() {
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            Seller seller = org.mockito.Mockito.mock(Seller.class);
            Product product = org.mockito.Mockito.mock(Product.class);
            Demand demandWithPayMethod = org.mockito.Mockito.mock(Demand.class);
            Demand demandWithoutPayMethod = org.mockito.Mockito.mock(Demand.class);
            BrandPayMethod payMethod = org.mockito.Mockito.mock(BrandPayMethod.class);

            when(groupBuyPublicService.getOrderSource(10L)).thenReturn(groupBuy);
            when(groupBuy.getSeller()).thenReturn(seller);
            when(groupBuy.getProduct()).thenReturn(product);
            when(groupBuy.getTitle()).thenReturn("제주 감귤 공동구매");
            when(seller.isSellable()).thenReturn(true);
            when(seller.getId()).thenReturn(20L);
            when(seller.getBusinessName()).thenReturn("문치프 농장");
            when(product.isOnSale()).thenReturn(true);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/image.jpg");
            when(product.getDemandBoardId()).thenReturn(30L);
            when(product.getId()).thenReturn(40L);
            when(product.getUnitPrice()).thenReturn(10_000);
            when(product.getShippingFee()).thenReturn(3_000);
            when(orderDemandService.getPaymentPendingForOrder(30L))
                .thenReturn(List.of(demandWithPayMethod, demandWithoutPayMethod));
            when(demandWithPayMethod.getMemberId()).thenReturn(1L);
            when(demandWithPayMethod.getId()).thenReturn(101L);
            when(demandWithPayMethod.getPayMethodId()).thenReturn(100L);
            when(demandWithPayMethod.getQuantity()).thenReturn(2);
            when(demandWithPayMethod.getDesiredPriceMax()).thenReturn(10_000);
            when(demandWithoutPayMethod.getMemberId()).thenReturn(2L);
            when(demandWithoutPayMethod.getId()).thenReturn(102L);
            when(demandWithoutPayMethod.getPayMethodId()).thenReturn(null);
            when(demandWithoutPayMethod.getQuantity()).thenReturn(1);
            when(demandWithoutPayMethod.getDesiredPriceMax()).thenReturn(10_000);
            when(entityManager.getReference(BrandPayMethod.class, 100L)).thenReturn(payMethod);
            when(orderMemberInfoService.getActiveMemberIds(Set.of(1L, 2L)))
                .thenReturn(Set.of(1L, 2L));

            Void result = orderService.autoCreateOrder(10L);

            assertThat(result).isNull();
            verify(ordersRepository).saveAll(ordersCaptor.capture());
            List<Orders> savedOrders = ordersCaptor.getValue();
            assertThat(savedOrders).hasSize(2);
            assertThat(savedOrders.get(0).getOrderNo()).startsWith("ORD-");
            assertThat(savedOrders.get(0).getMemberId()).isEqualTo(1L);
            assertThat(savedOrders.get(0).getBrandPayMethod()).isSameAs(payMethod);
            assertThat(savedOrders.get(0).getTotalAmount()).isEqualTo(20_000);
            assertThat(savedOrders.get(1).getMemberId()).isEqualTo(2L);
            assertThat(savedOrders.get(1).getBrandPayMethod()).isNull();
            assertThat(savedOrders.get(1).getTotalAmount()).isEqualTo(10_000);
        }

        @Test
        void 상품_단가가_희망_최고가를_초과하는_수요는_주문에서_제외한다() {
            Demand eligibleDemand = org.mockito.Mockito.mock(Demand.class);
            Demand expensiveDemand = org.mockito.Mockito.mock(Demand.class);
            when(eligibleDemand.getId()).thenReturn(101L);
            when(eligibleDemand.getMemberId()).thenReturn(1L);
            when(eligibleDemand.getQuantity()).thenReturn(2);
            when(eligibleDemand.getDesiredPriceMax()).thenReturn(10_000);
            when(expensiveDemand.getMemberId()).thenReturn(2L);
            when(expensiveDemand.getDesiredPriceMax()).thenReturn(9_999);
            prepareOrderSource(List.of(eligibleDemand, expensiveDemand));

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            assertThat(ordersCaptor.getValue())
                .singleElement()
                .extracting(Orders::getMemberId)
                .isEqualTo(1L);
            verify(preparedGroupBuy).increaseParticipantCount(1);
        }

        @Test
        void 상품_단가가_모든_수요의_희망_최고가를_초과하면_주문을_생성하지_않는다() {
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            Seller seller = org.mockito.Mockito.mock(Seller.class);
            Product product = org.mockito.Mockito.mock(Product.class);
            Demand firstDemand = org.mockito.Mockito.mock(Demand.class);
            Demand secondDemand = org.mockito.Mockito.mock(Demand.class);
            when(groupBuyPublicService.getOrderSource(10L)).thenReturn(groupBuy);
            when(groupBuy.getSeller()).thenReturn(seller);
            when(groupBuy.getProduct()).thenReturn(product);
            when(seller.isSellable()).thenReturn(true);
            when(seller.getId()).thenReturn(20L);
            when(product.isOnSale()).thenReturn(true);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/image.jpg");
            when(product.getUnitPrice()).thenReturn(10_000);
            when(product.getShippingFee()).thenReturn(3_000);
            when(product.getDemandBoardId()).thenReturn(30L);
            when(firstDemand.getMemberId()).thenReturn(1L);
            when(firstDemand.getDesiredPriceMax()).thenReturn(9_999);
            when(secondDemand.getMemberId()).thenReturn(2L);
            when(secondDemand.getDesiredPriceMax()).thenReturn(9_000);
            when(orderDemandService.getPaymentPendingForOrder(30L))
                .thenReturn(List.of(firstDemand, secondDemand));
            when(orderMemberInfoService.getActiveMemberIds(Set.of(1L, 2L)))
                .thenReturn(Set.of(1L, 2L));

            orderService.autoCreateOrder(10L);

            verify(ordersRepository, never()).saveAll(any());
            verify(groupBuy).increaseParticipantCount(0);
        }

        @Test
        void 탈퇴하지_않은_회원의_수요만_주문으로_생성한다() {
            Demand activeMemberDemand = org.mockito.Mockito.mock(Demand.class);
            Demand deletedMemberDemand = org.mockito.Mockito.mock(Demand.class);
            when(activeMemberDemand.getId()).thenReturn(101L);
            when(activeMemberDemand.getMemberId()).thenReturn(1L);
            when(activeMemberDemand.getQuantity()).thenReturn(2);
            when(activeMemberDemand.getDesiredPriceMax()).thenReturn(10_000);
            when(deletedMemberDemand.getMemberId()).thenReturn(2L);
            prepareOrderSource(List.of(activeMemberDemand, deletedMemberDemand));
            when(orderMemberInfoService.getActiveMemberIds(Set.of(1L, 2L)))
                .thenReturn(Set.of(1L));

            orderService.autoCreateOrder(10L);

            verify(ordersRepository).saveAll(ordersCaptor.capture());
            assertThat(ordersCaptor.getValue())
                .singleElement()
                .extracting(Orders::getMemberId)
                .isEqualTo(1L);
            verify(preparedGroupBuy).increaseParticipantCount(1);
        }

        @Test
        void 주문_가능한_상태의_수요가_없으면_주문을_생성하지_않는다() {
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            Seller seller = org.mockito.Mockito.mock(Seller.class);
            Product product = org.mockito.Mockito.mock(Product.class);
            when(groupBuyPublicService.getOrderSource(10L)).thenReturn(groupBuy);
            when(groupBuy.getSeller()).thenReturn(seller);
            when(groupBuy.getProduct()).thenReturn(product);
            when(seller.isSellable()).thenReturn(true);
            when(seller.getId()).thenReturn(20L);
            when(product.isOnSale()).thenReturn(true);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/image.jpg");
            when(product.getUnitPrice()).thenReturn(10_000);
            when(product.getShippingFee()).thenReturn(3_000);
            when(product.getDemandBoardId()).thenReturn(30L);
            when(orderDemandService.getPaymentPendingForOrder(30L)).thenReturn(List.of());
            when(orderMemberInfoService.getActiveMemberIds(Set.of())).thenReturn(Set.of());

            orderService.autoCreateOrder(10L);

            verify(ordersRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("주문 목록 조회 정상 테스트")
    class ViewOrderListTest {

        private final Pageable pageable = PageRequest.of(0, 20);
        private Orders order;

        @BeforeEach
        void setUp() {
            order = org.mockito.Mockito.mock(Orders.class);
            when(order.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 9, 1, 12, 0));
            when(order.getOrderNo()).thenReturn(ORDER_NO);
            when(order.getBusinessName()).thenReturn("문치프 농장");
            when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_COMPLETED);
            when(order.getImageUrl()).thenReturn("image.jpg");
            when(order.getProductName()).thenReturn("제주 감귤");
            when(order.getSum()).thenReturn(2);
            when(order.getTotalAmount()).thenReturn(23_000);
        }

        @Test
        void 전체_탭은_회원의_모든_주문을_조회한다() {
            when(ordersRepository.findAllByMemberId(MEMBER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderListResponse> result =
                orderService.viewOrderList(MEMBER_ID, OrderListTab.ALL, pageable);

            assertOrderListResponse(result.getContent().getFirst());
            verify(orderMemberInfoService).validateActiveMember(MEMBER_ID);
        }

        @Test
        void 진행중_탭은_진행중인_네가지_상태를_조회한다() {
            Set<OrderStatus> statuses = Set.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_COMPLETED,
                OrderStatus.PREPARING_SHIPMENT,
                OrderStatus.SHIPPED
            );
            when(ordersRepository.findAllByMemberIdAndOrderStatusIn(MEMBER_ID, statuses, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderListResponse> result =
                orderService.viewOrderList(MEMBER_ID, OrderListTab.IN_PROGRESS, pageable);

            assertOrderListResponse(result.getContent().getFirst());
        }

        @Test
        void 배송완료_탭은_배송완료_상태를_조회한다() {
            Set<OrderStatus> statuses = Set.of(OrderStatus.DELIVERED);
            when(ordersRepository.findAllByMemberIdAndOrderStatusIn(MEMBER_ID, statuses, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderListResponse> result =
                orderService.viewOrderList(MEMBER_ID, OrderListTab.DELIVERED, pageable);

            assertOrderListResponse(result.getContent().getFirst());
        }

        @Test
        void 구매확정_탭은_구매확정_상태를_조회한다() {
            Set<OrderStatus> statuses = Set.of(OrderStatus.COMPLEDED);
            when(ordersRepository.findAllByMemberIdAndOrderStatusIn(MEMBER_ID, statuses, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderListResponse> result =
                orderService.viewOrderList(MEMBER_ID, OrderListTab.COMPLETED, pageable);

            assertOrderListResponse(result.getContent().getFirst());
        }

        @Test
        void 주문_25건을_페이지_크기_20으로_조회하면_첫_페이지는_20건이고_두번째는_5건이다() {
            Pageable firstPageable = PageRequest.of(0, 20);
            Pageable secondPageable = PageRequest.of(1, 20);
            List<Orders> firstPageOrders = IntStream.rangeClosed(1, 20)
                .mapToObj(index -> index == 1 ? order : createOrder(index))
                .toList();
            List<Orders> secondPageOrders = IntStream.rangeClosed(21, 25)
                .mapToObj(this::createOrder)
                .toList();
            when(ordersRepository.findAllByMemberId(MEMBER_ID, firstPageable))
                .thenReturn(new PageImpl<>(firstPageOrders, firstPageable, 25));
            when(ordersRepository.findAllByMemberId(MEMBER_ID, secondPageable))
                .thenReturn(new PageImpl<>(secondPageOrders, secondPageable, 25));

            Page<OrderListResponse> firstPage =
                orderService.viewOrderList(MEMBER_ID, OrderListTab.ALL, firstPageable);
            Page<OrderListResponse> secondPage =
                orderService.viewOrderList(MEMBER_ID, OrderListTab.ALL, secondPageable);

            assertThat(firstPage.getNumber()).isZero();
            assertThat(firstPage.getNumberOfElements()).isEqualTo(20);
            assertThat(firstPage.getTotalElements()).isEqualTo(25);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(firstPage.isFirst()).isTrue();
            assertThat(firstPage.isLast()).isFalse();

            assertThat(secondPage.getNumber()).isEqualTo(1);
            assertThat(secondPage.getNumberOfElements()).isEqualTo(5);
            assertThat(secondPage.getTotalElements()).isEqualTo(25);
            assertThat(secondPage.getTotalPages()).isEqualTo(2);
            assertThat(secondPage.isFirst()).isFalse();
            assertThat(secondPage.isLast()).isTrue();

            verify(ordersRepository).findAllByMemberId(MEMBER_ID, firstPageable);
            verify(ordersRepository).findAllByMemberId(MEMBER_ID, secondPageable);
        }

        private Orders createOrder(int index) {
            Orders createdOrder = org.mockito.Mockito.mock(Orders.class);
            when(createdOrder.getCreatedAt())
                .thenReturn(LocalDateTime.of(2026, 9, 1, 12, 0));
            when(createdOrder.getOrderNo()).thenReturn("ORD-" + index);
            when(createdOrder.getBusinessName()).thenReturn("문치프 농장");
            when(createdOrder.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_COMPLETED);
            when(createdOrder.getImageUrl()).thenReturn("image.jpg");
            when(createdOrder.getProductName()).thenReturn("제주 감귤");
            when(createdOrder.getSum()).thenReturn(2);
            when(createdOrder.getTotalAmount()).thenReturn(20_000);
            return createdOrder;
        }

        private void assertOrderListResponse(OrderListResponse response) {
            assertThat(response.orderDate()).isEqualTo("2026-09-01");
            assertThat(response.orderNo()).isEqualTo(ORDER_NO);
            assertThat(response.businessName()).isEqualTo("문치프 농장");
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
            assertThat(response.imageUrl()).isEqualTo("image.jpg");
            assertThat(response.productName()).isEqualTo("제주 감귤");
            assertThat(response.quantity()).isEqualTo(2);
            assertThat(response.totalAmount()).isEqualTo(23_000);
        }
    }

    @Nested
    @DisplayName("주문 진행 요약 조회 정상 테스트")
    class GetOrderSummaryTest {

        @Test
        void 상태별_건수를_반환하고_조회되지_않은_상태는_0으로_채운다() {
            Set<OrderStatus> statuses = Set.of(
                OrderStatus.PAYMENT_COMPLETED,
                OrderStatus.PREPARING_SHIPMENT,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED
            );
            when(ordersRepository.countByMemberIdAndOrderStatusIn(MEMBER_ID, statuses))
                .thenReturn(List.of(
                    count(OrderStatus.PAYMENT_COMPLETED, 2),
                    count(OrderStatus.SHIPPED, 3),
                    count(OrderStatus.DELIVERED, 5)
                ));

            OrderSummaryResponse response = orderService.getSummary(MEMBER_ID);

            assertThat(response).isEqualTo(new OrderSummaryResponse(2, 0, 3, 5));
            verify(orderMemberInfoService).validateActiveMember(MEMBER_ID);
            verify(ordersRepository).countByMemberIdAndOrderStatusIn(MEMBER_ID, statuses);
        }

        private OrderStatusCount count(OrderStatus status, long count) {
            return new OrderStatusCount() {
                @Override
                public OrderStatus getOrderStatus() {
                    return status;
                }

                @Override
                public long getCount() {
                    return count;
                }
            };
        }
    }

    @Nested
    @DisplayName("주문 상세 조회 정상 테스트")
    class ViewOrderDetailTest {

        private Orders order;

        @BeforeEach
        void setUp() {
            order = org.mockito.Mockito.mock(Orders.class);
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            when(ordersRepository.findDetailByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.of(order));
            when(order.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 9, 1, 12, 0));
            when(order.getGroupBuy()).thenReturn(groupBuy);
            when(groupBuy.getId()).thenReturn(10L);
            when(groupBuy.getTitle()).thenReturn("제주 감귤 공동구매");
            when(order.getBusinessName()).thenReturn("문치프 농장");
            when(order.getDeliveryFee()).thenReturn(3_000);
            when(order.getOrderStatus()).thenReturn(OrderStatus.DELIVERED);
            when(order.getImageUrl()).thenReturn("image.jpg");
            when(order.getProductName()).thenReturn("제주 감귤");
            when(order.getSum()).thenReturn(2);
            when(order.getPrice()).thenReturn(10_000);
            when(order.getShippingName()).thenReturn("홍길동");
            when(order.getPhoneNumber()).thenReturn("encrypted-phone");
            when(encryptionService.decrypt("encrypted-phone")).thenReturn("01012345678");
            when(encryptionService.maskPhoneNumber("01012345678")).thenReturn("010-****-5678");
            when(orderPaymentInfoService.getForOrder(order))
                .thenReturn(new OrderPaymentInfo(20_000, 3_000, 23_000, "신한카드"));
        }

        @Test
        void 기본주소와_상세주소가_있으면_공백으로_합친다() {
            when(order.getAddress()).thenReturn("서울시 강남구");
            when(order.getAddressDetail()).thenReturn("101호");

            OrderDetailResponse result = orderService.viewOrderDetail(MEMBER_ID, ORDER_NO);

            assertThat(result.orderDate()).isEqualTo("2026-09-01");
            assertThat(result.orderNo()).isEqualTo(ORDER_NO);
            assertThat(result.product().groupBuy().groupBuyId()).isEqualTo(10L);
            assertThat(result.shipping().phoneNumberMasked()).isEqualTo("010-****-5678");
            assertThat(result.shipping().addressMasked()).isEqualTo("서울시 강남구 101호");
            assertThat(result.payment().totalPaymentAmount()).isEqualTo(23_000);
        }

        @Test
        void 기본주소가_비어있으면_상세주소만_반환한다() {
            when(order.getAddress()).thenReturn(" ");
            when(order.getAddressDetail()).thenReturn("101호");

            OrderDetailResponse result = orderService.viewOrderDetail(MEMBER_ID, ORDER_NO);

            assertThat(result.shipping().addressMasked()).isEqualTo("101호");
        }

        @Test
        void 두_주소가_null이면_null을_반환한다() {
            when(order.getAddress()).thenReturn(null);
            when(order.getAddressDetail()).thenReturn(null);

            OrderDetailResponse result = orderService.viewOrderDetail(MEMBER_ID, ORDER_NO);

            assertThat(result.shipping().addressMasked()).isNull();
        }
    }

    @Nested
    @DisplayName("주문 취소 정상 테스트")
    class OrderCancelTest {

        @Test
        void 결제대기_주문을_취소한다() {
            Orders order = org.mockito.Mockito.mock(Orders.class);
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            when(ordersRepository.findByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.of(order));
            when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_PENDING);
            when(order.getGroupBuy()).thenReturn(groupBuy);
            when(groupBuy.getId()).thenReturn(10L);

            orderService.orderCancel(MEMBER_ID, ORDER_NO);

            verify(groupBuyPublicService).decreaseParticipantCount(10L);
            verify(order).setOrderStatus(OrderStatus.CANCELED);
        }

        @Test
        void 이미_취소된_주문은_상태를_변경하지_않고_정상_종료한다() {
            Orders order = org.mockito.Mockito.mock(Orders.class);
            when(ordersRepository.findByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.of(order));
            when(order.getOrderStatus()).thenReturn(OrderStatus.CANCELED);

            orderService.orderCancel(MEMBER_ID, ORDER_NO);

            verify(groupBuyPublicService, never()).decreaseParticipantCount(any());
            verify(order, never()).setOrderStatus(any());
        }
    }

    @Nested
    @DisplayName("배송지 입력 정상 테스트")
    class UpdateShippingAddressTest {

        @Test
        void 전화번호에서_구분자를_제거하고_암호화해_배송지를_저장한다() {
            Orders order = org.mockito.Mockito.mock(Orders.class);
            OrderShippingAddressRequest request = new OrderShippingAddressRequest(
                "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호", "문 앞"
            );
            when(ordersRepository.findByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.of(order));
            when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_COMPLETED);
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted-phone");

            Void result = orderService.updateShippingAddress(MEMBER_ID, ORDER_NO, request);

            assertThat(result).isNull();
            verify(order).updateShipping(
                "홍길동", "encrypted-phone", "06234", "서울시 강남구", "101호", "문 앞"
            );
        }
    }
}
