package com.moongcheap_backend.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 업무 ID를 태그로 사용하지 않는다. 완료 카운터는 성공적으로 커밋된 변경만 센다. */
@Slf4j
@Component
public class LoadTestMetrics {
    private final MeterRegistry registry;

    public LoadTestMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 첫 업무 처리 이전부터 0을 노출해 최초 scrape에서 증가량을 놓치지 않게 한다.
        java.util.Map.of(
            "group_buy", java.util.List.of("created"),
            "order", java.util.List.of("created"),
            "judgment", java.util.List.of("success", "failed"),
            "payment_preparation", java.util.List.of("scheduled", "failed"),
            "payment_schedule_sync", java.util.List.of("scheduled", "removed"),
            "payment", java.util.List.of("succeeded", "failed", "review_required"),
            "payment_retry", java.util.List.of("scheduled"),
            "payment_attempt", java.util.List.of("initial", "reconciliation"),
            "outbox_publish", java.util.List.of("GROUP_BUY_ORDER_CREATION_REQUESTED", "GROUP_BUY_JUDGMENT_SCHEDULED"),
            "outbox_retry", java.util.List.of("GROUP_BUY_ORDER_CREATION_REQUESTED", "GROUP_BUY_JUDGMENT_SCHEDULED", "PAYMENT_SCHEDULE_SYNC")
        ).forEach((stage, outcomes) -> outcomes.forEach(outcome ->
            registry.counter("moongcheap.completed", "stage", stage, "outcome", outcome)));
    }

    public void committed(String stage, String outcome, long count, LocalDateTime origin) {
        Runnable record = () -> {
            try {
                registry.counter("moongcheap.completed", "stage", stage, "outcome", outcome).increment(count);
                if (origin != null && count > 0) {
                    Timer.builder("moongcheap.completion.delay").tags("stage", stage, "outcome", outcome)
                        .publishPercentileHistogram().minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofHours(2)).register(registry)
                        .record(Duration.ofMillis(Math.max(0, System.currentTimeMillis()
                            - origin.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli())));
                }
            } catch (RuntimeException exception) {
                log.warn("Could not record committed metrics: stage={}", stage);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { record.run(); }
            });
        }
        // 트랜잭션 없는 호출을 커밋된 결과로 표시하지 않는다.
    }
}
