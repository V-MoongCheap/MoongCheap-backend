package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEventStatus;
import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.payments.infrastructure.PaymentSchedule;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private final PaymentsRepository paymentsRepository;
    private final OutboxEventRepository outboxRepository;
    private final PaymentSchedule schedule;
    private final PaymentQueueProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestRedisRecovery(Long eventId) {
        paymentsRepository.configureLockTimeout();
        var candidate = outboxRepository.findById(eventId);
        if (candidate.isEmpty() || candidate.get().getEventType()
            != OutboxEventType.PAYMENT_SCHEDULE_SYNC) return;
        var payment = paymentsRepository.findByIdForUpdate(candidate.get().getAggregateId());
        var event = outboxRepository.findByIdForUpdate(eventId);
        if (payment.isEmpty() || event.isEmpty() || !payment.get().isAutomaticallyExecutable()) return;
        Double score = schedule.score(payment.get().getId());
        LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
        long nowMillis = now.atZone(ZONE_SEOUL).toInstant().toEpochMilli();
        long expected = event.get().getScheduledAt().atZone(ZONE_SEOUL)
            .toInstant().toEpochMilli();
        if (payment.get().getProcessingDeadline() != null) {
            expected = Math.max(expected, payment.get().getProcessingDeadline().toEpochMilli());
        }
        long normalUpperBound = Math.max(expected,
            nowMillis + properties.getVisibilityDelay().toMillis()) + 1_000;
        boolean missingOrStale = score == null || score + 1_000 < expected
            || score > normalUpperBound;
        if (event.get().getStatus() == OutboxEventStatus.PUBLISHED && missingOrStale) {
            event.get().requestPaymentSync(event.get().getScheduledAt(), now);
        }
    }

}
