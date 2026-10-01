package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import java.util.concurrent.atomic.AtomicLong;
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
public class PaymentRecoveryScheduler {
    private final OrdersRepository ordersRepository;
    private final OutboxEventRepository outboxRepository;
    private final PaymentPreparationService preparationService;
    private final PaymentRecoveryService recoveryService;
    private final PaymentQueueProperties properties;
    private final AtomicLong orderCursor = new AtomicLong();
    private final AtomicLong outboxCursor = new AtomicLong();

    @Scheduled(fixedDelayString = "${moongcheap.payments.queue.recovery-delay-ms:5000}")
    public void recover() {
        var orderIds = ordersRepository.findUnscheduledPaymentOrderIds(orderCursor.get(),
            properties.getBatchSize());
        if (orderIds.isEmpty()) orderCursor.set(0);
        for (Long orderId : orderIds) {
            orderCursor.set(orderId);
            try { preparationService.schedule(orderId); }
            catch (RuntimeException exception) {
                log.warn("Payment reservation deferred: orderId={}", orderId);
            }
        }
        var eventIds = outboxRepository.findIdsForRecovery(
            OutboxEventType.PAYMENT_SCHEDULE_SYNC, outboxCursor.get(),
            PageRequest.of(0, properties.getBatchSize()));
        if (eventIds.isEmpty()) outboxCursor.set(0);
        for (Long eventId : eventIds) {
            outboxCursor.set(eventId);
            try { recoveryService.requestRedisRecovery(eventId); }
            catch (RuntimeException exception) {
                log.warn("Payment schedule recovery deferred: eventId={}", eventId);
            }
        }
    }
}
