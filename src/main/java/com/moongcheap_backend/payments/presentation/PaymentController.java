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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment · 결제", description = "결제수단 및 결제 관리 API")
@Validated
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

    @Operation(summary = "브랜드페이 인증 콜백",
        description = "SDK가 전달한 code로 토큰을 발급하고 등록 결제수단을 서버에 동기화합니다.")
    @GetMapping("/brandpay/callback")
    public ResponseEntity<Void> callbackBrandPay(
        SessionPrincipal principal,
        @RequestParam @NotBlank String code,
        @RequestParam @NotBlank @Size(min = 2, max = 50) String customerKey
    ) {
        Long memberId = principal.memberId();

        brandPayTokenService.issue(memberId,
            new BrandPayAuthorizationRequest(customerKey, code));
        createPayMethodService.synchronize(memberId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 이미 인증된 사용자가 SDK에서 결제수단만 추가한 경우 토스의 최신 목록을 다시 읽어 서버 DB에 반영한다. 최초 인증 콜백은 위 callback API가
     * 발급과 동기화를 한 번에 처리하므로 이 API를 추가 호출할 필요가 없다.
     */
    @Operation(summary = "브랜드페이 결제수단 재동기화",
        description = "저장된 Access Token으로 토스의 최신 결제수단을 서버 DB에 동기화합니다.")
    @PostMapping("/brandpay/methods/synchronize")
    public ResponseEntity<Void> synchronizeBrandPayMethods(
        SessionPrincipal principal
    ) {
        createPayMethodService.synchronize(principal.memberId());
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

    @Operation(summary = "기본 결제수단 변경",
        description = "로그인한 회원의 활성 결제수단을 기본 결제수단으로 지정합니다.")
    @PatchMapping("/methods/{paymentMethodId}/default")
    public ResponseEntity<Void> changeDefaultPaymentMethod(
        SessionPrincipal principal,
        @PathVariable Long paymentMethodId) {
        paymentService.changeDefaultPaymentMethod(principal.memberId(), paymentMethodId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "결제 취소",
        description = "로그인한 회원의 승인 완료 결제를 토스페이먼츠에서 전액 취소합니다.")
    @PatchMapping("/{paymentId}")
    public ResponseEntity<Void> cancel(
        SessionPrincipal principal,
        @PathVariable Long paymentId,
        @RequestBody @Valid PaymentCancelRequestDto request) {
        paymentService.cancelPayment(
            principal.memberId(), paymentId, request.cancelReason());
        return ResponseEntity.noContent().build();
    }

}
