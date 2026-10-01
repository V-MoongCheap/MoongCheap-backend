package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient;
import com.moongcheap_backend.payments.infrastructure.PaymentGatewayException;
import com.moongcheap_backend.payments.infrastructure.PaymentReconciliationClient;
import com.moongcheap_backend.payments.infrastructure.PaymentSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentWorker {
    private final PaymentSchedule schedule;
    private final PaymentExecutionService executionService;
    private final BrandPayPaymentClient paymentClient;
    private final PaymentReconciliationClient reconciliationClient;

    @Transactional(propagation = Propagation.NEVER)
    public void runOne() {
        var candidate = schedule.claimDue();
        if (candidate.isEmpty()) return;
        runNow(candidate.get());
    }

    /**
     * 지정한 결제를 기존 워커와 동일한 실행 경로로 한 번 처리한다.
     * 로컬 테스트 콘솔처럼 이미 paymentId를 알고 있는 호출자가 Redis 폴링을
     * 기다리지 않고 검증할 때 사용하며, 상태 획득과 완료 반영은 기존 트랜잭션
     * 서비스가 담당한다.
     */
    void runNow(Long paymentId) {
        var begun = executionService.begin(paymentId);
        if (begun.isEmpty()) return;
        var work = begun.get();
        try {
            BrandPayPaymentClient.AutomaticPaymentResponse response;
            if (work.reconciliation()) {
                var existing = reconciliationClient.findByOrderId(work.request().orderId());
                if (existing.isPresent()) response = existing.get();
                else {
                    if (!executionService.allowReplay(work.paymentId(), work.processingToken())) return;
                    response = paymentClient.pay(work.request(), work.idempotencyKey());
                }
            } else {
                response = paymentClient.pay(work.request(), work.idempotencyKey());
            }
            executionService.complete(work.paymentId(), work.processingToken(), response);
        } catch (PaymentGatewayException exception) {
            executionService.handleError(work.paymentId(), work.processingToken(), exception,
                work.reconciliation());
        }
    }
}
