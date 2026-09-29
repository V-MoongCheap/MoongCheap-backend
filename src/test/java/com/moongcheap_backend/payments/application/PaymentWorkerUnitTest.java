package com.moongcheap_backend.payments.application;

import static org.mockito.Mockito.*;
import com.moongcheap_backend.payments.infrastructure.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentWorkerUnitTest {
    private final PaymentSchedule schedule = mock(PaymentSchedule.class);
    private final PaymentExecutionService execution = mock(PaymentExecutionService.class);
    private final BrandPayPaymentClient payment = mock(BrandPayPaymentClient.class);
    private final PaymentReconciliationClient reconciliation = mock(PaymentReconciliationClient.class);
    private final PaymentWorker worker = new PaymentWorker(schedule, execution, payment, reconciliation);
    private final UUID token = UUID.randomUUID();
    private final BrandPayPaymentClient.AutomaticPaymentRequest request =
        new BrandPayPaymentClient.AutomaticPaymentRequest("customer", "method",
            com.moongcheap_backend.payments.domain.enums.PaymentType.CARD, 1000, "order-1", "상품");
    private final BrandPayPaymentClient.AutomaticPaymentResponse response =
        new BrandPayPaymentClient.AutomaticPaymentResponse("pk", "order-1", "상품", 1000,
            "DONE", java.time.OffsetDateTime.parse("2026-09-22T09:00:00+09:00"));

    private void prepareReconciliation() {
        when(schedule.claimDue()).thenReturn(Optional.of(1L));
        when(execution.begin(1L)).thenReturn(Optional.of(new PaymentExecutionService.Execution(
            1L, token, request, "original-idempotency-key", true)));
    }

    @Test void 이전_결제가_성공했다면_조회결과로_완료하고_결제를_재요청하지_않는다() {
        prepareReconciliation();
        when(reconciliation.findByOrderId("order-1")).thenReturn(Optional.of(response));
        worker.runOne();
        var ordered = inOrder(reconciliation, execution);
        ordered.verify(reconciliation).findByOrderId("order-1");
        ordered.verify(execution).complete(1L, token, response);
        verifyNoInteractions(payment);
        verify(execution, never()).allowReplay(anyLong(), any());
    }

    @Test void 조회결과가_없고_재실행이_허용되면_원래_멱등키로_결제한다() {
        prepareReconciliation();
        when(reconciliation.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(execution.allowReplay(1L, token)).thenReturn(true);
        when(payment.pay(request, "original-idempotency-key")).thenReturn(response);
        worker.runOne();
        var ordered = inOrder(reconciliation, execution, payment);
        ordered.verify(reconciliation).findByOrderId("order-1");
        ordered.verify(execution).allowReplay(1L, token);
        ordered.verify(payment).pay(request, "original-idempotency-key");
        ordered.verify(execution).complete(1L, token, response);
    }

    @Test void 결과조회도_타임아웃이면_결제_재요청_없이_오류처리한다() {
        prepareReconciliation();
        var error = new PaymentGatewayException(PaymentGatewayException.Kind.UNKNOWN,
            "LOOKUP_TRANSPORT_ERROR");
        when(reconciliation.findByOrderId("order-1")).thenThrow(error);
        worker.runOne();
        verify(execution).handleError(1L, token, error, true);
        verify(execution, never()).complete(anyLong(), any(), any());
        verify(execution, never()).allowReplay(anyLong(), any());
        verifyNoInteractions(payment);
    }

    @Test void 재실행이_허용되지_않으면_결제를_재요청하지_않는다() {
        prepareReconciliation();
        when(reconciliation.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(execution.allowReplay(1L, token)).thenReturn(false);
        worker.runOne();
        verifyNoInteractions(payment);
        verify(execution, never()).complete(anyLong(), any(), any());
    }

    @Test void 후보가_없으면_DB와_토스를_호출하지_않는다() {
        var schedule = mock(PaymentSchedule.class);
        var execution = mock(PaymentExecutionService.class);
        var payment = mock(BrandPayPaymentClient.class);
        var reconciliation = mock(PaymentReconciliationClient.class);
        when(schedule.claimDue()).thenReturn(Optional.empty());
        new PaymentWorker(schedule, execution, payment, reconciliation).runOne();
        verifyNoInteractions(execution, payment, reconciliation);
    }
}
