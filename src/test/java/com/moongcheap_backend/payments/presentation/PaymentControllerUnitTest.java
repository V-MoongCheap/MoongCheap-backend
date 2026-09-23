package com.moongcheap_backend.payments.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.payments.application.BrandPayTokenService;
import com.moongcheap_backend.payments.application.CreatePayMethodService;
import com.moongcheap_backend.payments.application.CustomerKeyService;
import com.moongcheap_backend.payments.application.PaymentService;
import com.moongcheap_backend.payments.presentation.dto.BrandPayAuthorizationRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PaymentControllerUnitTest {

    @Mock CustomerKeyService customerKeyService;
    @Mock BrandPayTokenService brandPayTokenService;
    @Mock CreatePayMethodService createPayMethodService;
    @Mock PaymentService paymentService;
    @InjectMocks PaymentController controller;

    @Test
    void 운영용_GET_콜백은_토큰_발급과_결제수단_동기화_후_204를_반환한다() {
        SessionPrincipal principal = new SessionPrincipal(
            1L, "member", "회원", Set.of(), false, true);

        ResponseEntity<Void> response = controller.callbackBrandPay(
            principal, "authorization-code", "Secure_customerKey.1");

        verify(brandPayTokenService).issue(1L,
            new BrandPayAuthorizationRequest("Secure_customerKey.1", "authorization-code"));
        verify(createPayMethodService).synchronize(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
