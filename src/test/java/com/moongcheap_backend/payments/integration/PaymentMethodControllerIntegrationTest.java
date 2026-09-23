package com.moongcheap_backend.payments.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.payments.application.BrandPayMethodSyncService;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.domain.enums.ProviderCode;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.Card;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.MethodsResponse;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("결제수단 기본값 변경 통합 테스트")
class PaymentMethodControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private SessionTestHelper sessionTestHelper;
    @Autowired private BrandPayMethodRepository methodRepository;
    @Autowired private BrandPayMethodSyncService syncService;

    private Member member;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        member = memberFixture.save("결제수단유저");
        sessionCookie = sessionTestHelper.loginAs(member.getId());
    }

    @Test
    void 기본_결제수단을_변경하면_기존_기본값이_해제된다() throws Exception {
        BrandPayMethod oldDefault = methodRepository.save(new BrandPayMethod(
            member, "old-method", ProviderCode.CARD_KB,
            "1111", PaymentType.CARD, true));
        BrandPayMethod newDefault = methodRepository.save(new BrandPayMethod(
            member, "new-method", ProviderCode.BANK_SHINHAN,
            "2222", PaymentType.ACCOUNT, false));

        mockMvc.perform(patch("/api/payments/methods/" + newDefault.getId() + "/default")
                .cookie(sessionCookie))
            .andExpect(status().isNoContent());

        assertThat(methodRepository.findById(oldDefault.getId()).orElseThrow()
            .getIsDefault()).isFalse();
        assertThat(methodRepository.findById(newDefault.getId()).orElseThrow()
            .getIsDefault()).isTrue();
    }

    @Test
    void 다른_회원의_결제수단은_기본으로_변경할_수_없다() throws Exception {
        Member other = memberFixture.save("다른결제수단유저");
        BrandPayMethod otherMethod = methodRepository.save(new BrandPayMethod(
            other, "other-method", ProviderCode.CARD_KB,
            "3333", PaymentType.CARD, false));

        mockMvc.perform(patch("/api/payments/methods/" + otherMethod.getId() + "/default")
                .cookie(sessionCookie))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PAY_001"));
    }

    @Test
    void 기존_기본수단이_사라진_동기화에서도_새_기본수단은_한_건만_저장된다() {
        BrandPayMethod removedDefault = methodRepository.save(new BrandPayMethod(
            member, "removed-method", ProviderCode.CARD_KB,
            "1111", PaymentType.CARD, true));
        BrandPayMethod remaining = methodRepository.save(new BrandPayMethod(
            member, "remaining-method", ProviderCode.CARD_SAMSUNG,
            "2222", PaymentType.CARD, false));

        syncService.synchronize(member.getId(), new MethodsResponse(
            true,
            "remaining-id",
            List.of(new Card(
                "remaining-id", "remaining-method", "2222", "11", "ENABLED")),
            List.of()
        ));

        BrandPayMethod expired = methodRepository.findById(removedDefault.getId()).orElseThrow();
        BrandPayMethod newDefault = methodRepository.findById(remaining.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(PaymentsMethodStatus.EXPIRED);
        assertThat(expired.getIsDefault()).isFalse();
        assertThat(newDefault.getIsDefault()).isTrue();
    }

    @Test
    void 운영용_GET_콜백은_필수_쿼리_파라미터를_요구한다() throws Exception {
        mockMvc.perform(get("/api/payments/brandpay/callback")
                .cookie(sessionCookie))
            .andExpect(status().isBadRequest());
    }
}
