package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "moongcheap.payments.queue.enabled", havingValue = "true")
public class PaymentOutboxPublisher {
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private final OutboxEventRepository repository;
    private final PaymentOutboxPublishService service;
    private final PaymentQueueProperties properties;

    @Scheduled(fixedDelayString = "${moongcheap.payments.queue.outbox-publish-delay-ms:1000}")
    public void publishPending() {
        var ids = repository.findPublishableIds(OutboxEventType.PAYMENT_SCHEDULE_SYNC,
            LocalDateTime.now(ZONE_SEOUL), PageRequest.of(0, properties.getBatchSize()));
        for (Long id : ids) {
            try {
                service.publishOne(id);
            } catch (RuntimeException exception) {
                log.warn("Payment Outbox transaction deferred: eventId={}", id);
            }
        }
    }
}
