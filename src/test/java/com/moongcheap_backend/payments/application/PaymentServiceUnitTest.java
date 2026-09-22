package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.*;
import com.moongcheap_backend.payments.infrastructure.*;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
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
}
