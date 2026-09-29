package com.moongcheap_backend.common.metrics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.payments.application.*;
import com.moongcheap_backend.payments.infrastructure.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class ExecutionMetricsAspectTest {
    @Test void workerFailurePreservesExceptionAndReleasesBusyGauge() {
        var registry = new SimpleMeterRegistry();
        var schedule = mock(PaymentSchedule.class);
        when(schedule.claimDue()).thenThrow(new IllegalStateException("redis unavailable"));
        var target = new PaymentWorker(schedule, mock(PaymentExecutionService.class),
            mock(BrandPayPaymentClient.class), mock(PaymentReconciliationClient.class));
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new ExecutionMetricsAspect(registry));
        PaymentWorker proxy = factory.getProxy();
        assertThatThrownBy(proxy::runOne).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get("moongcheap.worker.active").gauge().value()).isZero();
        assertThat(registry.get("moongcheap.operation").tag("outcome", "error").timer().count()).isEqualTo(1);
    }

    @Test void interfaceImplementationGatewayCallsAreIntercepted() {
        var registry = new SimpleMeterRegistry();
        var factory = new AspectJProxyFactory(new DecliningGateway());
        factory.addAspect(new ExecutionMetricsAspect(registry));
        BrandPayPaymentClient proxy = factory.getProxy();
        assertThatThrownBy(() -> proxy.pay(null, "test" )).isInstanceOf(PaymentGatewayException.class);
        assertThat(registry.get("moongcheap.pg.request").tags("operation", "pay", "outcome", "DECLINED")
            .timer().count()).isEqualTo(1);
    }

    static class DecliningGateway implements BrandPayPaymentClient {
        public AutomaticPaymentResponse pay(AutomaticPaymentRequest request, String key) {
            throw new PaymentGatewayException(PaymentGatewayException.Kind.DECLINED, "test");
        }
    }
}
