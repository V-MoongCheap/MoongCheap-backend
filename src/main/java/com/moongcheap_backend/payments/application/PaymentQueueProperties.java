package com.moongcheap_backend.payments.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "moongcheap.payments.queue")
public class PaymentQueueProperties {
    private boolean enabled;
    private int workers = 1;
    private Duration visibilityDelay = Duration.ofSeconds(60);
    private Duration lease = Duration.ofSeconds(60);
    private int batchSize = 100;
    private int maxAttempts = 5;
    private Duration replayWindow = Duration.ofDays(14);
    private Duration shutdownWait = Duration.ofSeconds(15);

    public void validate() {
        if (workers < 1 || workers > 64 || batchSize < 1 || batchSize > 1000
            || visibilityDelay.isNegative() || visibilityDelay.isZero()
            || lease.compareTo(Duration.ofSeconds(10)) < 0 || maxAttempts < 1
            || replayWindow.isNegative() || replayWindow.isZero()
            || replayWindow.compareTo(Duration.ofDays(14)) > 0
            || shutdownWait.isNegative()) {
            throw new IllegalArgumentException("결제 큐 설정 범위가 유효하지 않습니다.");
        }
    }
}
