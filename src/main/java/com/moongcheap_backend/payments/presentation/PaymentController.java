package com.moongcheap_backend.payments.presentation;

import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.payments.application.BrandPayTokenService;
import com.moongcheap_backend.payments.application.CreatePayMethodService;
import com.moongcheap_backend.payments.application.CustomerKeyService;
import com.moongcheap_backend.payments.application.PaymentService;
import com.moongcheap_backend.payments.presentation.dto.BrandPayAuthorizationRequest;
import com.moongcheap_backend.payments.presentation.dto.GetCustomerKeyResponse;
import com.moongcheap_backend.payments.presentation.dto.PaymentCancelRequestDto;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment · 결제", description = "결제수단 및 결제 관리 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final CustomerKeyService customerKeyService;
    private final BrandPayTokenService brandPayTokenService;
    private final CreatePayMethodService createPayMethodService;
    private final PaymentService paymentService;

    /**
     * 브랜드페이 SDK 초기화에 사용할 로그인 회원의 CustomerKey를 반환한다.
     */
    @Operation(summary = "customerKey 획득",
        description = "로그인한 회원의 customerKey를 조회합니다.")
    @GetMapping("/brandpay/customer-key")
    public ResponseEntity<GetCustomerKeyResponse> getCustomerKey(
        SessionPrincipal principal
    ) {
        return ResponseEntity.ok(customerKeyService.getCustomerKey(principal));
    }

    /**
     * 프론트엔드가 전달한 SDK 인증 결과를 토큰으로 교환한 뒤, 토스에 등록된 결제수단을 서버 DB에 동기화한다. 성공 시 응답 본문에 민감한 정보를 싣지 않고 204를
     * 반환한다.
     */
    @Operation(summary = "브랜드페이 인증 및 결제수단 등록 완료",
        description = "SDK redirectUrl의 code로 토큰을 발급하고 등록 결제수단을 서버에 동기화합니다.")
    @PostMapping("/brandpay/authorization")
    public ResponseEntity<Void> authorizeBrandPay(
        SessionPrincipal principal,
        @RequestBody @Valid BrandPayAuthorizationRequest request
    ) {
        Long memberId = principal.memberId();

        // SDK 인증 코드를 토큰으로 교환해 저장한 후, 해당 토큰으로 결제수단을 동기화한다.
        brandPayTokenService.issue(memberId, request);
        createPayMethodService.synchronize(memberId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "결제수단 목록 조회",
        description = "로그인한 회원의 만료되지 않은 결제수단을 조회합니다.")
    @GetMapping("/methods")
    public ResponseEntity<List<PaymentMethodResponseDto>> list(
        SessionPrincipal principal) {
        return ResponseEntity.ok(paymentService
            .getPaymentMethods(principal.memberId()));
    }

    @Operation(summary = "결제수단 삭제",
        description = "로그인한 회원의 결제수단을 토스에서 삭제하고 서버에서 만료 처리합니다.")
    @DeleteMapping("/methods/{paymentMethodId}")
    public ResponseEntity<Void> delete(
        SessionPrincipal principal,
        @PathVariable Long paymentMethodId) {
        paymentService.deletePaymentMethod(principal.memberId(), paymentMethodId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "결제 취소",
        description = "기능 ID 없음. 로그인한 회원의 결제를 취소합니다.")
    @PatchMapping("/{paymentId}")
    public ResponseEntity<Void> cancel(
        SessionPrincipal principal,
        @PathVariable Long paymentId,
        @RequestBody @Valid PaymentCancelRequestDto request) {
        throw new UnsupportedOperationException("PaymentService 구현이 필요합니다.");
    }

    //기본결제수단 변경
}
