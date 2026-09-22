package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.application.PaymentPreparationService.Preparation;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentPreparationServiceUnitTest {

    @Mock
    private OrdersRepository ordersRepository;

    @Mock
    private PaymentsRepository paymentsRepository;

    @InjectMocks
    private PaymentPreparationService service;

    private Orders order;

    @BeforeEach
    void setUp() {
        order = Orders.create(
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
        when(ordersRepository.findByIdForPaymentUpdate(100L))
            .thenReturn(Optional.of(order));
    }

    @Test
    void 토스_호출_전에_READY_결제_이력을_생성한다() {
        when(paymentsRepository.findFirstByOrdersIdAndStatusInOrderByIdDesc(
            eq(100L), anyCollection()
        )).thenReturn(Optional.empty());
        when(paymentsRepository.saveAndFlush(any(Payments.class)))
            .thenAnswer(invocation -> {
                Payments payment = invocation.getArgument(0);
                ReflectionTestUtils.setField(payment, "id", 200L);
                return payment;
            });

        Preparation result = service.prepare(100L, PaymentsMethod.CARD);

        assertThat(result).isEqualTo(new Preparation(200L, true));
        ArgumentCaptor<Payments> captor = ArgumentCaptor.forClass(Payments.class);
        verify(paymentsRepository).saveAndFlush(captor.capture());
        Payments saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentsStatus.READY);
        assertThat(saved.getPaymentKey()).isNull();
        assertThat(saved.getOrderNo()).isEqualTo("ORD-automatic-1");
        assertThat(saved.getTotalAmount()).isEqualTo(20_000);
        assertThat(saved.getMethod()).isEqualTo(PaymentsMethod.CARD);
    }

    @Test
    void 재시도는_기존_READY_결제_이력을_재사용한다() {
        Payments payment = Payments.readyBrandPay(
            order,
            "ORD-automatic-1",
            "공동구매 상품",
            20_000,
            PaymentsMethod.CARD
        );
        ReflectionTestUtils.setField(payment, "id", 200L);
        when(paymentsRepository.findFirstByOrdersIdAndStatusInOrderByIdDesc(
            eq(100L), anyCollection()
        )).thenReturn(Optional.of(payment));

        Preparation result = service.prepare(100L, PaymentsMethod.CARD);

        assertThat(result).isEqualTo(new Preparation(200L, true));
        verify(paymentsRepository, never()).saveAndFlush(any());
    }
}
