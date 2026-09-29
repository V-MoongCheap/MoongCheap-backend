package com.moongcheap_backend.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** 프록시를 통과하는 서비스 호출의 경과시간. 하위 서비스/DB/외부 호출 대기를 포함한다. */
@Slf4j
@Aspect
@Component
@Order(-1)
@RequiredArgsConstructor
public class ServiceMetricsAspect {
    private final MeterRegistry registry;

    @Around("execution(public * com.moongcheap_backend..*(..)) && "
        + "@within(org.springframework.stereotype.Service)")
    public Object measure(ProceedingJoinPoint call) throws Throwable {
        String service = ClassUtils.getUserClass(call.getTarget()).getName();
        String method = call.getSignature().getName();
        long started = System.nanoTime();
        String outcome = "returned";
        try {
            return call.proceed();
        } catch (Throwable failure) {
            outcome = "error";
            throw failure;
        } finally {
            long elapsed = System.nanoTime() - started;
            try {
                Timer.builder("moongcheap.service.execution")
                    .description("Service method wall time including nested calls and transaction completion when owned")
                    .tags("service", service, "method", method, "outcome", outcome)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofMinutes(5))
                    .register(registry).record(elapsed, TimeUnit.NANOSECONDS);
            } catch (RuntimeException metricError) {
                // 계측 오류로 업무 반환값/예외를 변경하지 않는다.
                log.warn("Could not record service metrics: service={}, method={}", service, method);
            }
        }
    }
}
