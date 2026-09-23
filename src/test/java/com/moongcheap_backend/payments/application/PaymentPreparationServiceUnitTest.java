package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.domain.*;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.*;
import com.moongcheap_backend.payments.domain.enums.*;
import com.moongcheap_backend.payments.infrastructure.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentPreparationServiceUnitTest {
    OrdersRepository orders = mock(OrdersRepository.class);
    PaymentsRepository payments = mock(PaymentsRepository.class);
    CustomerKeyRepository customers = mock(CustomerKeyRepository.class);
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    BrandPayIdempotencyKeyGenerator keys = mock(BrandPayIdempotencyKeyGenerator.class);
    PaymentPreparationService service = new PaymentPreparationService(
        orders, payments, customers, outbox, keys);

    @Test void 결제와_Outbox를_같은_예약에서_생성한다() {
        Member member = Member.builder().loginId("user").nickname("user").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod method = new BrandPayMethod(member, "method", ProviderCode.CARD_KB,
            "1234", PaymentType.CARD, true);
        GroupBuy group = new GroupBuy(null, null, "상품", 1, 1,
            LocalDateTime.now(), GroupBuyStatus.RECRUITMENT_COMPLETED);
        Orders order = Orders.create("order-100", 10L, 1L, method, group,
            1L, "상품", "img", 2, 10000, 0, 1L, "판매자");
        ReflectionTestUtils.setField(order, "id", 100L);
        when(orders.findByIdForPaymentUpdate(100L)).thenReturn(Optional.of(order));
        when(payments.findFirstByOrdersIdOrderByIdDesc(100L)).thenReturn(Optional.empty());
        when(customers.findById(1L)).thenReturn(Optional.of(new CustomerKey(member, "customer")));
        when(keys.forAutomaticPayment("order-100")).thenReturn("fixed-key");
        when(payments.saveAndFlush(any())).thenAnswer(inv -> {
            Payments p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 200L);
            return p;
        });

        assertThat(service.schedule(100L)).isEqualTo(200L);

        var payment = org.mockito.ArgumentCaptor.forClass(Payments.class);
        verify(payments).saveAndFlush(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentsStatus.PENDING);
        assertThat(payment.getValue().getIdempotencyKey()).isEqualTo("fixed-key");
        verify(outbox).save(argThat(event -> event.getAggregateId().equals(200L)
            && event.getEventType() == com.moongcheap_backend.common.outbox.domain.OutboxEventType.PAYMENT_SCHEDULE_SYNC));
    }

    @Test void 기존_결제는_새_결제로_대체하지_않는다() {
        Payments previous = mock(Payments.class);
        when(previous.getId()).thenReturn(200L);
        when(orders.findByIdForPaymentUpdate(100L)).thenReturn(Optional.of(mock(Orders.class)));
        when(payments.findFirstByOrdersIdOrderByIdDesc(100L)).thenReturn(Optional.of(previous));
        assertThat(service.schedule(100L)).isEqualTo(200L);
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }
}
