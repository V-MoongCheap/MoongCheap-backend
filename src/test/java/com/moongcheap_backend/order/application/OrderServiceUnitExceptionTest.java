package com.moongcheap_backend.order.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.application.demand.OrderDemandService;
import com.moongcheap_backend.groupbuy.application.GroupBuyPublicService;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.member.application.OrderMemberInfoService;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.order.presentation.OrderController.OrderListTab;
import com.moongcheap_backend.order.presentation.dto.OrderShippingAddressRequest;
import com.moongcheap_backend.payments.application.PaymentPublicService;
import com.moongcheap_backend.product.domain.product.Product;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitExceptionTest {

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

    @Nested
    @DisplayName("자동 주문 예외 테스트")
    class AutoCreateOrderExceptionTest {

        private GroupBuy groupBuy;
        private Seller seller;
        private Product product;

        @BeforeEach
        void setUp() {
            groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            seller = org.mockito.Mockito.mock(Seller.class);
            product = org.mockito.Mockito.mock(Product.class);
            when(groupBuyPublicService.getOrderSource(10L)).thenReturn(groupBuy);
            when(groupBuy.getSeller()).thenReturn(seller);
            when(groupBuy.getProduct()).thenReturn(product);
        }

        @Test
        void 판매자_상태가_WITHDRAWN으로_삭제되었으면_주문을_생성할_수_없다() {
            when(seller.getDeletedAt()).thenReturn(LocalDateTime.now());

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.SELLER_NOT_FOUND);

            verify(ordersRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        }

        @ParameterizedTest
        @EnumSource(value = SellerStatus.class, names = {"PENDING", "BLOCKED"})
        void 판매자_상태가_PENDING_또는_BLOCKED이면_주문을_생성할_수_없다(
            SellerStatus sellerStatus
        ) {
            when(seller.isSellable())
                .thenAnswer(invocation -> sellerStatus == SellerStatus.APPROVED);

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.SELLER_NOT_APPROVED);

            verify(ordersRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void 판매중이_아닌_상품이면_주문을_생성할_수_없다() {
            when(seller.isSellable()).thenReturn(true);
            when(product.isOnSale()).thenReturn(false);

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.PRODUCT_NOT_ORDERABLE);
        }

        @Test
        void 상품의_판매자가_공동구매_판매자와_다르면_주문을_생성할_수_없다() {
            prepareOrderableSellerAndProduct();
            when(seller.getId()).thenReturn(20L);
            when(product.getSellerId()).thenReturn(21L);

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.PRODUCT_NOT_ORDERABLE);
        }

        @Test
        void 상품_썸네일이_null이면_주문을_생성할_수_없다() {
            prepareOrderableSellerAndProduct();
            when(seller.getId()).thenReturn(20L);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn(null);

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.PRODUCT_NOT_ORDERABLE);
        }

        @Test
        void 상품_썸네일이_공백이면_주문을_생성할_수_없다() {
            prepareOrderableSellerAndProduct();
            when(seller.getId()).thenReturn(20L);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn(" ");

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.PRODUCT_NOT_ORDERABLE);
        }

        @Test
        void 상품_단가가_null이면_주문을_생성할_수_없다() {
            prepareProductValidationBeforePrice();
            when(product.getUnitPrice()).thenReturn(null);

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.PRODUCT_NOT_ORDERABLE);
        }

        @Test
        void 상품_배송비가_null이면_주문을_생성할_수_없다() {
            prepareProductValidationBeforePrice();
            when(product.getUnitPrice()).thenReturn(10_000);
            when(product.getShippingFee()).thenReturn(null);

            assertBusinessException(() -> orderService.autoCreateOrder(10L),
                ErrorCode.PRODUCT_NOT_ORDERABLE);
        }

        private void prepareProductValidationBeforePrice() {
            prepareOrderableSellerAndProduct();
            when(seller.getId()).thenReturn(20L);
            when(product.getSellerId()).thenReturn(20L);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/image.jpg");
        }

        private void prepareOrderableSellerAndProduct() {
            when(seller.isSellable()).thenReturn(true);
            when(product.isOnSale()).thenReturn(true);
        }
    }

    @Nested
    @DisplayName("주문 목록 조회 예외 테스트")
    class ViewOrderListExceptionTest {

        @Test
        void 탈퇴하거나_비활성화된_회원이면_주문_목록을_조회할_수_없다() {
            doThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .when(orderMemberInfoService).validateActiveMember(MEMBER_ID);

            assertBusinessException(
                () -> orderService.viewOrderList(
                    MEMBER_ID, OrderListTab.ALL, Pageable.unpaged()
                ),
                ErrorCode.MEMBER_NOT_FOUND
            );
            verifyNoInteractions(ordersRepository);
        }
    }

    @Nested
    @DisplayName("주문 상세 조회 예외 테스트")
    class ViewOrderDetailExceptionTest {

        @Test
        void 탈퇴하거나_비활성화된_회원이면_주문_상세를_조회할_수_없다() {
            doThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .when(orderMemberInfoService).validateActiveMember(MEMBER_ID);

            assertBusinessException(
                () -> orderService.viewOrderDetail(MEMBER_ID, ORDER_NO),
                ErrorCode.MEMBER_NOT_FOUND
            );
            verifyNoInteractions(ordersRepository);
        }

        @Test
        void 회원의_주문을_찾을_수_없으면_예외가_발생한다() {
            when(ordersRepository.findDetailByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.empty());

            assertBusinessException(() -> orderService.viewOrderDetail(MEMBER_ID, ORDER_NO),
                ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("주문 취소 예외 테스트")
    class OrderCancelExceptionTest {

        @Test
        void 탈퇴하거나_비활성화된_회원이면_주문을_취소할_수_없다() {
            doThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .when(orderMemberInfoService).validateActiveMember(MEMBER_ID);

            assertBusinessException(
                () -> orderService.orderCancel(MEMBER_ID, ORDER_NO),
                ErrorCode.MEMBER_NOT_FOUND
            );
            verifyNoInteractions(ordersRepository);
        }

        @Test
        void 회원의_주문을_찾을_수_없으면_예외가_발생한다() {
            when(ordersRepository.findByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.empty());

            assertBusinessException(() -> orderService.orderCancel(MEMBER_ID, ORDER_NO),
                ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void 결제대기_상태가_아니면_취소할_수_없다() {
            Orders order = org.mockito.Mockito.mock(Orders.class);
            when(ordersRepository.findByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.of(order));
            when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_COMPLETED);

            assertBusinessException(() -> orderService.orderCancel(MEMBER_ID, ORDER_NO),
                ErrorCode.ORDER_CANNOT_CANCEL);
        }
    }

    @Nested
    @DisplayName("배송지 입력 예외 테스트")
    class UpdateShippingAddressExceptionTest {

        @Test
        void 탈퇴하거나_비활성화된_회원이면_배송지를_수정할_수_없다() {
            OrderShippingAddressRequest request = new OrderShippingAddressRequest(
                "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호", null
            );
            doThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .when(orderMemberInfoService).validateActiveMember(MEMBER_ID);

            assertBusinessException(
                () -> orderService.updateShippingAddress(MEMBER_ID, ORDER_NO, request),
                ErrorCode.MEMBER_NOT_FOUND
            );
            verifyNoInteractions(ordersRepository);
            verifyNoInteractions(encryptionService);
        }

        @Test
        void 결제완료_상태가_아니면_배송지를_입력할_수_없다() {
            Orders order = org.mockito.Mockito.mock(Orders.class);
            OrderShippingAddressRequest request = new OrderShippingAddressRequest(
                "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호", null
            );
            when(ordersRepository.findByOrderNoAndMemberId(ORDER_NO, MEMBER_ID))
                .thenReturn(Optional.of(order));
            when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_PENDING);

            assertBusinessException(
                () -> orderService.updateShippingAddress(MEMBER_ID, ORDER_NO, request),
                ErrorCode.ORDER_CANNOT_SHIPPING
            );
            verify(encryptionService, never()).encrypt(org.mockito.ArgumentMatchers.anyString());
        }
    }

    private void assertBusinessException(Runnable executable, ErrorCode expectedErrorCode) {
        assertThatThrownBy(executable::run)
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(expectedErrorCode);
    }
}
