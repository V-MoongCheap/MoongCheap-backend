package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.domain.OutboxEventStatus;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.payments.infrastructure.PaymentSchedule;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxPublishService {
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private final PaymentsRepository paymentsRepository;
    private final OutboxEventRepository outboxRepository;
    private final PaymentSchedule schedule;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long eventId) {
        paymentsRepository.configureLockTimeout();
        var candidate = outboxRepository.findById(eventId);
        if (candidate.isEmpty() || candidate.get().getEventType()
            != OutboxEventType.PAYMENT_SCHEDULE_SYNC) return;
        Long paymentId = candidate.get().getAggregateId();
        var payment = paymentsRepository.findByIdForUpdate(paymentId);
        var event = outboxRepository.findByIdForUpdate(eventId);
        if (event.isEmpty() || event.get().getEventType()
            != OutboxEventType.PAYMENT_SCHEDULE_SYNC) return;
        LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
        if (event.get().getStatus() != OutboxEventStatus.PENDING
            || event.get().getNextAttemptAt().isAfter(now)) return;
        try {
            if (payment.isPresent() && payment.get().isAutomaticallyExecutable()) {
                LocalDateTime score = event.get().getScheduledAt();
                if (payment.get().getProcessingDeadline() != null) {
                    LocalDateTime deadline = LocalDateTime.ofInstant(
                        payment.get().getProcessingDeadline(), ZONE_SEOUL);
                    if (deadline.isAfter(score)) score = deadline;
                }
                schedule.schedule(paymentId, score);
            } else {
                schedule.remove(paymentId);
            }
            event.get().markPublished(now);
        } catch (RuntimeException exception) {
            event.get().scheduleRetry(now);
            log.warn("Payment Outbox publish deferred: eventId={}", eventId);
        }
    }
}
