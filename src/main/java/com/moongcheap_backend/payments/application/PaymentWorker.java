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
        var begun = executionService.begin(candidate.get());
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
