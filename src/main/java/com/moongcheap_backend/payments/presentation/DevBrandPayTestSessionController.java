package com.moongcheap_backend.payments.presentation;

import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.payments.application.BrandPayTokenService;
import com.moongcheap_backend.payments.application.CreatePayMethodService;
import com.moongcheap_backend.payments.application.DevBrandPayTestPaymentService;
import com.moongcheap_backend.payments.application.DevBrandPayTestPaymentService.TestPaymentResult;
import com.moongcheap_backend.payments.application.DevBrandPayTestOrderService;
import com.moongcheap_backend.payments.application.DevBrandPayTestOrderService.TestOrderResult;
import com.moongcheap_backend.payments.presentation.dto.BrandPayAuthorizationRequest;
import com.moongcheap_backend.payments.presentation.dto.DevBrandPayTestOrderRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BrandPay 로컬 연동 테스트에만 사용하는 임시 세션 발급 API다.
 * local/dev 프로필에서만 빈이 생성되므로 운영 환경에서는 엔드포인트 자체가 존재하지 않는다.
 */
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/dev/brandpay-test")
@RequiredArgsConstructor
public class DevBrandPayTestSessionController {

    private static final String TEST_LOGIN_ID = "brandpay-test-user";

    private final MemberRepository memberRepository;
    private final PrincipalFactory principalFactory;
    private final AuthSessionManager sessionManager;
    private final BrandPayTokenService brandPayTokenService;
    private final CreatePayMethodService createPayMethodService;
    private final DevBrandPayTestOrderService testOrderService;
    private final DevBrandPayTestPaymentService testPaymentService;

    /** 테스트 회원을 재사용하거나 새로 만든 뒤 약관 동의된 구매자 세션을 발급한다. */
    @PostMapping("/login")
    @Transactional
    public ResponseEntity<SessionPrincipal> login(HttpServletRequest request) {
        Member member = memberRepository.findByLoginIdAndDeletedAtIsNull(TEST_LOGIN_ID)
            .orElseGet(() -> memberRepository.save(Member.builder()
                .loginId(TEST_LOGIN_ID)
                .nickname("brandpay-test")
                .email("brandpay-test@localhost")
                .build()));

        if (!member.isTermsAgreed()) {
            member.agreeTerms();
        }

        SessionPrincipal principal = principalFactory.build(member);
        sessionManager.bindPrincipal(request, principal, false);
        return ResponseEntity.ok(principal);
    }

    /** 중간 인증 상태에 걸린 테스트 customerKey를 피할 수 있도록 새 회원 세션을 만든다. */
    @PostMapping("/fresh-login")
    @Transactional
    public ResponseEntity<SessionPrincipal> freshLogin(HttpServletRequest request) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Member member = Member.builder()
            .loginId("bp-test-" + suffix)
            .nickname("bp-test-" + suffix)
            .email("bp-test-" + suffix + "@localhost")
            .build();
        member.agreeTerms();
        memberRepository.saveAndFlush(member);

        SessionPrincipal principal = principalFactory.build(member);
        sessionManager.bindPrincipal(request, principal, false);
        return ResponseEntity.ok(principal);
    }

    /**
     * 토스 SDK가 Authorization Code를 전달하는 로컬 전용 콜백이다.
     * 프론트 JavaScript를 한 번 더 거치지 않고 즉시 토큰 교환과 결제수단 동기화를
     * 완료한다. SDK의 교차 출처 요청을 정적 프론트 서버로 다시 리다이렉트하면
     * 불필요한 CORS preflight가 발생하므로 성공 응답만 반환한다.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
        SessionPrincipal principal,
        @RequestParam String code,
        @RequestParam String customerKey
    ) {
        brandPayTokenService.issue(principal.memberId(),
            new BrandPayAuthorizationRequest(customerKey, code));
        createPayMethodService.synchronize(principal.memberId());
        return ResponseEntity.noContent().build();
    }

    /** 활성 결제수단을 지정한 PAYMENT_PENDING 테스트 주문을 생성한다. */
    @PostMapping("/orders")
    public ResponseEntity<TestOrderResult> createPendingOrder(
        SessionPrincipal principal,
        @RequestBody @Valid DevBrandPayTestOrderRequest request
    ) {
        return ResponseEntity.ok(testOrderService.create(
            principal.memberId(), request.paymentMethodId(), request.amount(),
            request.orderName()));
    }

    /**
     * 로그인한 테스트 회원의 기존 주문을 운영 자동결제 경로로 즉시 한 번 실행한다.
     * local/dev 프로필에서만 제공되며 주문보다 Payments가 먼저 생성되는 규칙을 지킨다.
     */
    @PostMapping("/payments/{orderId}/execute")
    public ResponseEntity<TestPaymentResult> executePayment(
        SessionPrincipal principal,
        @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(testPaymentService.execute(principal.memberId(), orderId));
    }
}
