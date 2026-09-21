package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.domain.enums.ProviderCode;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.Account;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.Card;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.MethodsResponse;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import java.util.List;
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
class BrandPayMethodSyncServiceUnitTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BrandPayMethodRepository brandPayMethodRepository;

    @InjectMocks
    private BrandPayMethodSyncService service;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder().loginId("member").nickname("member").build();
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Test
    void 전체_응답을_methodKey로_등록_갱신하고_누락된_수단은_만료시킨다() {
        BrandPayMethod existingCard = new BrandPayMethod(
            member, "card-method-key", ProviderCode.CARD_SAMSUNG,
            "old-card-number", PaymentType.CARD, false);
        BrandPayMethod missingMethod = new BrandPayMethod(
            member, "missing-method-key", ProviderCode.BANK_KB,
            "old-account-number", PaymentType.ACCOUNT, true);
        MethodsResponse response = new MethodsResponse(
            true,
            "card-id",
            List.of(new Card(
                "card-id", "card-method-key", "1234-****-****-5678",
                "11", "ENABLED")),
            List.of(new Account(
                "account-id", "account-method-key", "110-***-123456",
                "88", "ENABLED"))
        );
        when(memberRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(Optional.of(member));
        when(brandPayMethodRepository.findAllByMemberId(1L))
            .thenReturn(List.of(existingCard, missingMethod));

        service.synchronize(1L, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BrandPayMethod>> captor = ArgumentCaptor.forClass(List.class);
        verify(brandPayMethodRepository).saveAllAndFlush(captor.capture());
        List<BrandPayMethod> savedMethods = captor.getValue();

        assertThat(savedMethods).hasSize(3);
        assertThat(existingCard.getProviderCode()).isEqualTo(ProviderCode.CARD_KB);
        assertThat(existingCard.getMaskedNumber()).isEqualTo("1234-****-****-5678");
        assertThat(existingCard.getIsDefault()).isTrue();
        assertThat(existingCard.getStatus()).isEqualTo(PaymentsMethodStatus.ACTIVE);

        assertThat(missingMethod.getIsDefault()).isFalse();
        assertThat(missingMethod.getStatus()).isEqualTo(PaymentsMethodStatus.EXPIRED);

        BrandPayMethod newAccount = savedMethods.stream()
            .filter(method -> method.getMethodKey().equals("account-method-key"))
            .findFirst()
            .orElseThrow();
        assertThat(newAccount.getProviderCode()).isEqualTo(ProviderCode.BANK_SHINHAN);
        assertThat(newAccount.getType()).isEqualTo(PaymentType.ACCOUNT);
        assertThat(newAccount.getIsDefault()).isFalse();
        assertThat(newAccount.getStatus()).isEqualTo(PaymentsMethodStatus.ACTIVE);
    }
}
