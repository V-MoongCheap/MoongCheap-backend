package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 로컬 테스트 콘솔에서 운영 자동결제 흐름을 한 건씩 검증하는 서비스다. */
@Profile({"local", "dev"})
@Service
@RequiredArgsConstructor
public class DevBrandPayTestPaymentService {

    private final OrdersRepository ordersRepository;
    private final PaymentService paymentService;
    private final PaymentWorker paymentWorker;
    private final PaymentsRepository paymentsRepository;

    /**
     * 로그인 회원의 주문을 결제 예약한 뒤 기존 워커 실행 경로로 즉시 한 번 처리한다.
     * Toss 요청 전에 {@link Payments}와 Outbox가 먼저 저장되므로 테스트 API도
     * 운영 결제의 추적성과 멱등성 규칙을 그대로 따른다.
     */
    public TestPaymentResult execute(Long memberId, Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        var order = ordersRepository.findByIdForAutomaticPayment(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long paymentId = paymentService.scheduleAutomaticPayment(orderId);
        paymentWorker.runNow(paymentId);

        Payments payment = paymentsRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return new TestPaymentResult(
            payment.getId(),
            payment.getOrderNo(),
            payment.getOrderName(),
            payment.getTotalAmount(),
            payment.getStatus(),
            payment.getAttemptCount()
        );
    }

    public record TestPaymentResult(
        Long paymentId,
        String orderId,
        String orderName,
        Integer amount,
        PaymentsStatus status,
        int attemptCount
    ) {
    }
}
