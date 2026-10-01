package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevBrandPayTestPaymentServiceUnitTest {

    private final OrdersRepository ordersRepository = mock(OrdersRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentWorker paymentWorker = mock(PaymentWorker.class);
    private final PaymentsRepository paymentsRepository = mock(PaymentsRepository.class);
    private final DevBrandPayTestPaymentService service =
        new DevBrandPayTestPaymentService(
            ordersRepository, paymentService, paymentWorker, paymentsRepository);

    @Test
    void 회원_주문은_결제를_먼저_예약하고_기존_워커로_즉시_실행한다() {
        Orders order = mock(Orders.class);
        Payments payment = mock(Payments.class);
        when(order.getMemberId()).thenReturn(1L);
        when(ordersRepository.findByIdForAutomaticPayment(10L))
            .thenReturn(Optional.of(order));
        when(paymentService.scheduleAutomaticPayment(10L)).thenReturn(20L);
        when(paymentsRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(payment.getId()).thenReturn(20L);
        when(payment.getOrderNo()).thenReturn("order-10");
        when(payment.getOrderName()).thenReturn("테스트 상품");
        when(payment.getTotalAmount()).thenReturn(1_000);
        when(payment.getStatus()).thenReturn(PaymentsStatus.SUCCEEDED);
        when(payment.getAttemptCount()).thenReturn(1);

        var result = service.execute(1L, 10L);

        verify(paymentService).scheduleAutomaticPayment(10L);
        verify(paymentWorker).runNow(20L);
        assertThat(result.paymentId()).isEqualTo(20L);
        assertThat(result.status()).isEqualTo(PaymentsStatus.SUCCEEDED);
        assertThat(result.amount()).isEqualTo(1_000);
    }

    @Test
    void 다른_회원의_주문은_실행하지_않는다() {
        Orders order = mock(Orders.class);
        when(order.getMemberId()).thenReturn(2L);
        when(ordersRepository.findByIdForAutomaticPayment(10L))
            .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.execute(1L, 10L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(paymentService, never()).scheduleAutomaticPayment(10L);
        verify(paymentWorker, never()).runNow(20L);
    }
}
