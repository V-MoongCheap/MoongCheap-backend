package com.moongcheap_backend.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.moongcheap_backend.payments.infrastructure.PaymentGatewayException;

@lombok.extern.slf4j.Slf4j
@Aspect
@Component
@Order(0) // 트랜잭션 interceptor 바깥에서 커밋/롤백까지 포함한 호출 시간을 측정한다.
public class ExecutionMetricsAspect {
    private final MeterRegistry registry;
    private final AtomicInteger busy = new AtomicInteger();

    public ExecutionMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("moongcheap.worker.active", busy);
    }

    @Around("execution(* com.moongcheap_backend.payments.application.PaymentWorker.runOne(..)) || "
        + "execution(* com.moongcheap_backend.payments.application.GroupPaymentReservationService.scheduleForGroup(..)) || "
        + "execution(* com.moongcheap_backend.payments.application.PaymentPreparationService.schedule(..)) || "
        + "execution(* com.moongcheap_backend.payments.application.PaymentExecutionService.*(..)) || "
        + "execution(* com.moongcheap_backend.groupbuy.application.GroupBuyService.createGroupBuy(..)) || "
        + "execution(* com.moongcheap_backend.groupbuy.application.GroupBuyJudgmentService.judgeAndPay(..)) || "
        + "execution(* com.moongcheap_backend.order.application.OrderService.autoCreateOrder(..)) || "
        + "execution(* com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient+.pay(..)) || "
        + "execution(* com.moongcheap_backend.payments.infrastructure.PaymentReconciliationClient+.findByOrderId(..))")
    public Object measure(ProceedingJoinPoint call) throws Throwable {
        String method = call.getSignature().getName();
        boolean worker = method.equals("runOne");
        boolean gateway = method.equals("pay") || method.equals("findByOrderId");
        if (worker) busy.incrementAndGet();
        long start = System.nanoTime();
        String outcome = "returned";
        try {
            return call.proceed();
        } catch (Throwable error) {
            outcome = error instanceof PaymentGatewayException pg ? pg.kind().name() : "error";
            throw error;
        } finally {
            if (worker) busy.decrementAndGet();
            try {
                Timer.builder(gateway ? "moongcheap.pg.request" : "moongcheap.operation")
                    .tags("operation", method, "outcome", outcome)
                    .publishPercentileHistogram().minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofMinutes(5)).register(registry)
                    .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (RuntimeException metricError) {
                log.warn("Could not record operation metrics: operation={}", method);
            }
        }
    }
}
