package com.moongcheap_backend.payments.presentation;

import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.payments.presentation.dto.PaymentCancelRequestDto;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodRegisterRequestDto;
import com.moongcheap_backend.payments.presentation.dto.PaymentMethodResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
public class PaymentController {

    @Operation(summary = "결제수단 등록", description = "기능 ID 없음. 로그인한 회원의 결제수단을 등록합니다.")
    @PostMapping("/methods")
    public ResponseEntity<Void> create(
        SessionPrincipal principal,
        @RequestBody @Valid PaymentMethodRegisterRequestDto request) {
        throw new UnsupportedOperationException("PaymentService 구현이 필요합니다.");
    }

    @Operation(summary = "결제수단 목록 조회", description = "기능 ID 없음. 로그인한 회원이 등록한 결제수단을 조회합니다.")
    @GetMapping("/methods")
    public ResponseEntity<List<PaymentMethodResponseDto>> list(
        SessionPrincipal principal) {
        throw new UnsupportedOperationException("PaymentService 구현이 필요합니다.");
    }

    @Operation(summary = "결제수단 삭제", description = "기능 ID 없음. 로그인한 회원이 등록한 결제수단을 삭제합니다.")
    @DeleteMapping("/methods/{paymentMethodId}")
    public ResponseEntity<Void> delete(
        SessionPrincipal principal,
        @PathVariable Long paymentMethodId) {
        throw new UnsupportedOperationException("PaymentService 구현이 필요합니다.");
    }

    @Operation(summary = "결제 취소", description = "기능 ID 없음. 로그인한 회원의 결제를 취소합니다.")
    @PatchMapping("/{paymentId}")
    public ResponseEntity<Void> cancel(
        SessionPrincipal principal,
        @PathVariable Long paymentId,
        @RequestBody @Valid PaymentCancelRequestDto request) {
        throw new UnsupportedOperationException("PaymentService 구현이 필요합니다.");
    }
}
