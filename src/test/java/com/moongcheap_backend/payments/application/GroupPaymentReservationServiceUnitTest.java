package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupPaymentReservationServiceUnitTest {
    @Test void 큐가_활성화되면_성사한_공동구매의_주문을_예약한다() {
        OrdersRepository orders = mock(OrdersRepository.class);
        PaymentPreparationService preparation = mock(PaymentPreparationService.class);
        PaymentQueueProperties properties = new PaymentQueueProperties();
        properties.setEnabled(true);
        properties.setBatchSize(2);
        when(orders.findUnscheduledPaymentOrderIdsByGroupBuy(10L, 0, 2))
            .thenReturn(List.of(100L, 101L));
        when(orders.findUnscheduledPaymentOrderIdsByGroupBuy(10L, 101L, 2))
            .thenReturn(List.of());

        int count = new GroupPaymentReservationService(orders, preparation, properties)
            .scheduleForGroup(10L);

        assertThat(count).isEqualTo(2);
        verify(preparation).schedule(100L);
        verify(preparation).schedule(101L);
    }

    @Test void 큐가_비활성화되면_주문을_조회하지_않는다() {
        OrdersRepository orders = mock(OrdersRepository.class);
        PaymentPreparationService preparation = mock(PaymentPreparationService.class);
        PaymentQueueProperties properties = new PaymentQueueProperties();
        assertThat(new GroupPaymentReservationService(orders, preparation, properties)
            .scheduleForGroup(10L)).isZero();
        verifyNoInteractions(orders, preparation);
    }
}
