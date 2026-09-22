package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.domain.enums.PaymentsType;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionServiceUnitTest {

    @Mock
    private OrdersRepository ordersRepository;

    @Mock
    private PaymentsRepository paymentsRepository;

    @InjectMocks
    private PaymentCompletionService service;

    @Test
    void 자동결제_결과와_주문완료를_함께_반영한다() {
        Orders order = Orders.create(
            "ORD-automatic-1",
            10L,
            1L,
            null,
            org.mockito.Mockito.mock(GroupBuy.class),
            20L,
            "공동구매 상품",
            "image.jpg",
            2,
            10_000,
            3_000,
            30L,
            "문치프 상점"
        );
        ReflectionTestUtils.setField(order, "id", 100L);
        Payments payment = Payments.readyBrandPay(
            order,
            "ORD-automatic-1",
            "공동구매 상품",
            20_000,
            PaymentsMethod.CARD
        );
        ReflectionTestUtils.setField(payment, "id", 200L);
        AutomaticPaymentResponse response = new AutomaticPaymentResponse(
            "payment-key",
            "ORD-automatic-1",
            "공동구매 상품",
            20_000,
            "DONE",
            OffsetDateTime.parse("2026-09-21T12:00:00+09:00")
        );

        when(ordersRepository.findByIdForPaymentUpdate(100L))
            .thenReturn(Optional.of(order));
        when(paymentsRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(payment));

        service.completeAutomaticPayment(100L, 200L, response);

        assertThat(payment.getOrders()).isSameAs(order);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key");
        assertThat(payment.getOrderNo()).isEqualTo("ORD-automatic-1");
        assertThat(payment.getTotalAmount()).isEqualTo(20_000);
        assertThat(payment.getStatus()).isEqualTo(PaymentsStatus.DONE);
        assertThat(payment.getType()).isEqualTo(PaymentsType.BRANDPAY);
        assertThat(payment.getMethod()).isEqualTo(PaymentsMethod.CARD);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
    }
}
