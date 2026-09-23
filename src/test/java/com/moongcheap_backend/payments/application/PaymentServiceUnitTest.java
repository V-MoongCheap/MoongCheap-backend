package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.*;
import com.moongcheap_backend.payments.infrastructure.*;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {
    @Mock BrandPayMethodRepository methodRepository;
    @Mock BrandPayTokenService tokenService;
    @Mock BrandPayMethodClient methodClient;
    @Mock BrandPayIdempotencyKeyGenerator keys;
    @Mock PaymentPreparationService preparation;
    @Mock PaymentsRepository paymentsRepository;
    @Mock PaymentCancellationClient cancellationClient;
    @InjectMocks PaymentService service;

    @Test void EXPIRED를_제외하고_기본_결제수단부터_조회한다() {
        Member member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod card = new BrandPayMethod(member, "method", ProviderCode.CARD_KB,
            "1234", PaymentType.CARD, true);
        ReflectionTestUtils.setField(card, "id", 10L);
        when(methodRepository.findAllByMemberIdAndStatusNot(eq(1L),
            eq(PaymentsMethodStatus.EXPIRED), any(Sort.class))).thenReturn(List.of(card));
        assertThat(service.getPaymentMethods(1L)).containsExactly(
            new PaymentMethodResponseDto(10L, "KB국민카드", "1234", true,
                PaymentsMethodStatus.ACTIVE));
    }

    @Test void 회원의_결제수단을_토스에서_삭제하고_EXPIRED로_변경한다() {
        Member member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod card = new BrandPayMethod(member, "method", ProviderCode.CARD_KB,
            "1234", PaymentType.CARD, true);
        ReflectionTestUtils.setField(card, "id", 10L);
        when(methodRepository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(card));
        when(tokenService.getValidAccessToken(1L)).thenReturn("token");
        when(keys.forPaymentMethodRemoval(1L, 10L, "method")).thenReturn("key");
        service.deletePaymentMethod(1L, 10L);
        verify(methodClient).remove("token", "method", PaymentType.CARD, "key");
        assertThat(card.getStatus()).isEqualTo(PaymentsMethodStatus.EXPIRED);
    }

    @Test void 자동결제_호출은_외부청구없이_예약만_생성한다() {
        when(preparation.schedule(100L)).thenReturn(200L);
        service.executeAutomaticPayment(100L);
        verify(preparation).schedule(100L);
    }

    @Test void 승인된_회원_결제를_전액_취소하고_주문을_환불완료로_변경한다() {
        Payments payment = succeededPayment();
        OffsetDateTime canceledAt = OffsetDateTime.parse("2026-09-22T15:30:00+09:00");
        when(paymentsRepository.findByIdAndMemberIdForCancellation(200L, 1L))
            .thenReturn(Optional.of(payment));
        when(keys.forPaymentCancellation(200L, "payment-key"))
            .thenReturn("cancel-idempotency-key");
        when(cancellationClient.cancel("payment-key", "고객 요청",
            "cancel-idempotency-key")).thenReturn(
            new PaymentCancellationClient.CancellationResponse(
                "payment-key", "ORDER-100", "CANCELED", canceledAt, "고객 요청"));

        service.cancelPayment(1L, 200L, "고객 요청");

        assertThat(payment.getStatus()).isEqualTo(PaymentsStatus.CANCELED);
        assertThat(payment.getCancelReason()).isEqualTo("고객 요청");
        assertThat(payment.getCanceledAt()).isEqualTo(
            LocalDateTime.of(2026, 9, 22, 15, 30));
        assertThat(payment.getOrders().getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(paymentsRepository).saveAndFlush(payment);
    }

    @Test void 이미_취소된_결제는_토스를_다시_호출하지_않는다() {
        Payments payment = succeededPayment();
        payment.cancel("고객 요청", LocalDateTime.now());
        when(paymentsRepository.findByIdAndMemberIdForCancellation(200L, 1L))
            .thenReturn(Optional.of(payment));

        service.cancelPayment(1L, 200L, "고객 요청");

        verifyNoInteractions(cancellationClient);
    }

    private Payments succeededPayment() {
        Orders order = Orders.create("ORDER-100", 10L, 1L, null, null,
            20L, "공동구매 상품", "image", 1, 10_000, 0,
            30L, "판매자");
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setOrderStatus(OrderStatus.PAYMENT_COMPLETED);

        Payments payment = Payments.readyBrandPay(order, "ORDER-100",
            "공동구매 상품", 10_000, PaymentsMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", 200L);
        payment.completeBrandPay("payment-key", LocalDateTime.now());
        return payment;
    }
}
