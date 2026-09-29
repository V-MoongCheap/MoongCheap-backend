package com.moongcheap_backend.payments.infrastructure;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 배포 시 결정하는 설정. 런타임 설정 변경은 지원하지 않는다. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "moongcheap.payments.gateway")
public class PaymentGatewayProperties {
    public enum Mode { TOSS, MOCK }

    private Mode mode = Mode.TOSS;
    private Duration mockDelay = Duration.ofMillis(300);

    @PostConstruct
    public void validate() {
        if (mode == null || mockDelay == null || mockDelay.isNegative()
            || mockDelay.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("결제 gateway 설정이 유효하지 않습니다.");
        }
    }
}
