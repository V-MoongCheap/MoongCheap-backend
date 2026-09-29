package com.moongcheap_backend.payments.application;

import static org.mockito.Mockito.*;
import com.moongcheap_backend.payments.infrastructure.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentWorkerUnitTest {
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
