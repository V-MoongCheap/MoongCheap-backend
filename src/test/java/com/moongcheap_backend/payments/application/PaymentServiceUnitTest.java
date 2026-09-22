package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.CustomerKey;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.domain.enums.ProviderCode;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentRequest;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import com.moongcheap_backend.payments.infrastructure.CustomerKeyRepository;
import com.moongcheap_backend.payments.application.PaymentPreparationService.Preparation;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {

    @Mock
    private BrandPayMethodRepository brandPayMethodRepository;

    @Mock
    private BrandPayTokenService brandPayTokenService;

    @Mock
    private BrandPayMethodClient brandPayMethodClient;

    @Mock
    private BrandPayIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Mock
    private OrdersRepository ordersRepository;

    @Mock
    private CustomerKeyRepository customerKeyRepository;

    @Mock
    private BrandPayPaymentClient brandPayPaymentClient;

    @Mock
    private PaymentPreparationService paymentPreparationService;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @InjectMocks
    private PaymentService service;

    @Test
    void EXPIRED를_제외하고_기본_결제수단부터_조회한다() {
        Member member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod card = new BrandPayMethod(
            member,
            "server-only-method-key",
            ProviderCode.CARD_KB,
            "1234-****-****-5678",
            PaymentType.CARD,
            true
        );
        ReflectionTestUtils.setField(card, "id", 10L);

        when(brandPayMethodRepository.findAllByMemberIdAndStatusNot(
            eq(1L), eq(PaymentsMethodStatus.EXPIRED), any(Sort.class)
        )).thenReturn(List.of(card));

        List<PaymentMethodResponseDto> result = service.getPaymentMethods(1L);

        assertThat(result).containsExactly(new PaymentMethodResponseDto(
            10L,
            "KB국민카드",
            "1234-****-****-5678",
            true,
            PaymentsMethodStatus.ACTIVE
        ));

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(brandPayMethodRepository).findAllByMemberIdAndStatusNot(
            eq(1L), eq(PaymentsMethodStatus.EXPIRED), sortCaptor.capture());
        assertThat(sortCaptor.getValue().getOrderFor("isDefault").isDescending()).isTrue();
    }

    @Test
    void 회원의_결제수단을_토스에서_삭제하고_EXPIRED로_변경한다() {
        Member member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod card = new BrandPayMethod(
            member,
            "card-method-key",
            ProviderCode.CARD_KB,
            "1234-****-****-5678",
            PaymentType.CARD,
            true
        );
        ReflectionTestUtils.setField(card, "id", 10L);

        when(brandPayMethodRepository.findByIdAndMemberId(10L, 1L))
            .thenReturn(Optional.of(card));
        when(brandPayTokenService.getValidAccessToken(1L))
            .thenReturn("valid-access-token");
        when(idempotencyKeyGenerator.forPaymentMethodRemoval(
            1L, 10L, "card-method-key"
        )).thenReturn("removal-idempotency-key");

        service.deletePaymentMethod(1L, 10L);

        verify(brandPayMethodClient).remove(
            "valid-access-token",
            "card-method-key",
            PaymentType.CARD,
            "removal-idempotency-key"
        );
        assertThat(card.getStatus()).isEqualTo(PaymentsMethodStatus.EXPIRED);
        assertThat(card.getIsDefault()).isFalse();
        verify(brandPayMethodRepository).saveAndFlush(card);
    }

    @Test
    void 이미_EXPIRED인_결제수단_삭제는_외부_API를_다시_호출하지_않는다() {
        Member member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod expiredCard = new BrandPayMethod(
            member,
            "card-method-key",
            ProviderCode.CARD_KB,
            "1234-****-****-5678",
            PaymentType.CARD,
            true
        );
        ReflectionTestUtils.setField(expiredCard, "id", 10L);
        expiredCard.expire();

        when(brandPayMethodRepository.findByIdAndMemberId(10L, 1L))
            .thenReturn(Optional.of(expiredCard));

        service.deletePaymentMethod(1L, 10L);

        verifyNoInteractions(
            brandPayTokenService,
            brandPayMethodClient,
            idempotencyKeyGenerator
        );
        verify(brandPayMethodRepository, never()).saveAndFlush(any());
    }

    @Test
    void 주문의_활성_결제수단으로_브랜드페이_자동결제를_실행한다() {
        Member member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        BrandPayMethod card = new BrandPayMethod(
            member,
            "card-method-key",
            ProviderCode.CARD_KB,
            "1234-****-****-5678",
            PaymentType.CARD,
            true
        );
        Orders order = org.mockito.Mockito.mock(Orders.class);
        when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_PENDING);
        when(order.getBrandPayMethod()).thenReturn(card);
        when(order.getMemberId()).thenReturn(1L);
        when(order.getOrderNo()).thenReturn("ORD-automatic-1");
        when(order.getProductName()).thenReturn("공동구매 상품");
        when(order.getTotalAmount()).thenReturn(20_000);

        CustomerKey customerKey = new CustomerKey(member, "Secure_customerKey.1");
        AutomaticPaymentResponse response = new AutomaticPaymentResponse(
            "payment-key",
            "ORD-automatic-1",
            "공동구매 상품",
            20_000,
            "DONE",
            OffsetDateTime.parse("2026-09-21T12:00:00+09:00")
        );

        when(ordersRepository.findByIdForAutomaticPayment(100L))
            .thenReturn(Optional.of(order));
        when(customerKeyRepository.findById(1L)).thenReturn(Optional.of(customerKey));
        when(idempotencyKeyGenerator.forAutomaticPayment("ORD-automatic-1"))
            .thenReturn("payment-idempotency-key");
        when(paymentPreparationService.prepare(
            100L,
            com.moongcheap_backend.payments.domain.enums.PaymentsMethod.CARD
        )).thenReturn(new Preparation(200L, true));
        when(brandPayPaymentClient.pay(any(), eq("payment-idempotency-key")))
            .thenReturn(response);

        service.executeAutomaticPayment(100L);

        InOrder executionOrder = inOrder(
            paymentPreparationService,
            brandPayPaymentClient,
            paymentCompletionService
        );
        executionOrder.verify(paymentPreparationService).prepare(
            100L,
            com.moongcheap_backend.payments.domain.enums.PaymentsMethod.CARD
        );
        ArgumentCaptor<AutomaticPaymentRequest> requestCaptor =
            ArgumentCaptor.forClass(AutomaticPaymentRequest.class);
        executionOrder.verify(brandPayPaymentClient).pay(
            requestCaptor.capture(), eq("payment-idempotency-key"));
        assertThat(requestCaptor.getValue()).isEqualTo(new AutomaticPaymentRequest(
            "Secure_customerKey.1",
            "card-method-key",
            PaymentType.CARD,
            20_000,
            "ORD-automatic-1",
            "공동구매 상품"
        ));
        executionOrder.verify(paymentCompletionService).completeAutomaticPayment(
            100L,
            200L,
            response
        );
    }

    @Test
    void 이미_결제_완료된_주문은_자동결제를_다시_호출하지_않는다() {
        Orders order = org.mockito.Mockito.mock(Orders.class);
        when(order.getOrderStatus()).thenReturn(OrderStatus.PAYMENT_COMPLETED);
        when(ordersRepository.findByIdForAutomaticPayment(100L))
            .thenReturn(Optional.of(order));

        service.executeAutomaticPayment(100L);

        verifyNoInteractions(
            customerKeyRepository,
            brandPayPaymentClient,
            paymentPreparationService,
            paymentCompletionService
        );
    }
}
