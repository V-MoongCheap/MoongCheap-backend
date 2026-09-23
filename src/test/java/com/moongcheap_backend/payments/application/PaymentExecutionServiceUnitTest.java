package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.common.outbox.domain.*;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.domain.*;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.order.domain.*;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.*;
import com.moongcheap_backend.payments.domain.enums.*;
import com.moongcheap_backend.payments.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentExecutionServiceUnitTest {
    static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    OrdersRepository orders = mock(OrdersRepository.class);
    PaymentsRepository payments = mock(PaymentsRepository.class);
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    PaymentQueueProperties properties = new PaymentQueueProperties();
    PaymentExecutionService service = new PaymentExecutionService(orders, payments, outbox, properties);
    Payments payment;
    OutboxEvent event;

    @BeforeEach void setUp() {
        Member member = Member.builder().loginId("user").nickname("user").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod method = new BrandPayMethod(member, "method", ProviderCode.CARD_KB,
            "1234", PaymentType.CARD, true);
        GroupBuy group = new GroupBuy(null, null, "상품", 1, 1,
            LocalDateTime.now(), GroupBuyStatus.RECRUITMENT_COMPLETED);
        Orders order = Orders.create("order-100", 10L, 1L, method, group,
            1L, "상품", "img", 2, 10000, 0, 1L, "판매자");
        ReflectionTestUtils.setField(order, "id", 100L);
        payment = Payments.readyBrandPay(order, "order-100", "상품", 20000, PaymentsMethod.CARD);
        payment.schedule(method, "customer", "same-key");
        ReflectionTestUtils.setField(payment, "id", 200L);
        ReflectionTestUtils.setField(payment, "createdAt",
            LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneId.of("Asia/Seoul")));
        event = OutboxEvent.paymentScheduleSync(200L,
            LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")),
            LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")));
        when(payments.findOrderId(200L)).thenReturn(Optional.of(100L));
        when(orders.findByIdForPaymentUpdate(100L)).thenReturn(Optional.of(order));
        when(payments.findByIdForUpdate(200L)).thenReturn(Optional.of(payment));
        when(payments.databaseNow()).thenReturn(NOW);
        when(outbox.findByTypeAndAggregateIdForUpdate(
            OutboxEventType.PAYMENT_SCHEDULE_SYNC, 200L)).thenReturn(Optional.of(event));
    }

    @Test void begin은_소유권과_UNKNOWN을_커밋할_상태로_만든다() {
        var execution = service.begin(200L).orElseThrow();
        assertThat(execution.idempotencyKey()).isEqualTo("same-key");
        assertThat(execution.reconciliation()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentsStatus.UNKNOWN);
        assertThat(payment.getAttemptCount()).isEqualTo(1);
        assertThat(payment.getProcessingToken()).isEqualTo(execution.processingToken());
    }

    @Test void 오래된_토큰은_성공결과를_반영하지_못한다() {
        var execution = service.begin(200L).orElseThrow();
        service.complete(200L, UUID.randomUUID(), new BrandPayPaymentClient.AutomaticPaymentResponse(
            "pk", "order-100", "상품", 20000, "DONE", OffsetDateTime.now()));
        assertThat(payment.getStatus()).isEqualTo(PaymentsStatus.UNKNOWN);
        assertThat(payment.getOrders().getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(payment.getProcessingToken()).isEqualTo(execution.processingToken());
    }

    @Test void 현재_토큰은_결제와_주문을_함께_완료하고_Outbox를_요청한다() {
        var execution = service.begin(200L).orElseThrow();
        service.complete(200L, execution.processingToken(),
            new BrandPayPaymentClient.AutomaticPaymentResponse("pk", "order-100", "상품",
                20000, "DONE", OffsetDateTime.parse("2026-09-22T09:00:00+09:00")));
        assertThat(payment.getStatus()).isEqualTo(PaymentsStatus.SUCCEEDED);
        assertThat(payment.getOrders().getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }
}
