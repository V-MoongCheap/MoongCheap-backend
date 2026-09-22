package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentRequest;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import com.moongcheap_backend.payments.infrastructure.PaymentGatewayException;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExecutionService {
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private final OrdersRepository ordersRepository;
    private final PaymentsRepository paymentsRepository;
    private final OutboxEventRepository outboxRepository;
    private final PaymentQueueProperties properties;

    public record Execution(Long paymentId, UUID processingToken,
        AutomaticPaymentRequest request, String idempotencyKey, boolean reconciliation) {
        @Override public String toString() { return "PaymentExecution[redacted]"; }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Execution> begin(Long paymentId) {
        paymentsRepository.configureLockTimeout();
        var orderId = paymentsRepository.findOrderId(paymentId);
        if (orderId.isEmpty()) return Optional.empty();
        var order = ordersRepository.findByIdForPaymentUpdate(orderId.get());
        if (order.isEmpty()) return Optional.empty();
        var found = paymentsRepository.findByIdForUpdate(paymentId);
        if (found.isEmpty()) return Optional.empty();
        Payments payment = found.get();
        LocalDateTime now = nowLocal();
        OutboxEvent outbox = outboxRepository.findByTypeAndAggregateIdForUpdate(
            OutboxEventType.PAYMENT_SCHEDULE_SYNC, paymentId).orElse(null);
        if (outbox == null) {
            payment.requireReview();
            outboxRepository.save(OutboxEvent.paymentScheduleSync(paymentId, now, now));
            return Optional.empty();
        }
        if (!payment.isAutomaticallyExecutable()) {
            // Redis에 남은 종료 작업은 Outbox를 통해 제거한다.
            outbox.requestPaymentSync(now, now);
            return Optional.empty();
        }
        if (outbox.getScheduledAt().isAfter(now)) {
            return Optional.empty();
        }
        Instant instant = paymentsRepository.databaseNow();
        if (payment.getProcessingToken() != null
            && payment.getProcessingDeadline().isAfter(instant)) return Optional.empty();
        if (payment.getAttemptCount() >= properties.getMaxAttempts()
            || payment.getCreatedAt() == null
            || !now.isBefore(payment.getCreatedAt().plus(properties.getReplayWindow()))) {
            payment.requireReview();
            outbox.requestPaymentSync(now, now);
            return Optional.empty();
        }
        var method = payment.getBrandPayMethod();
        if (method == null || payment.getCustomerKeySnapshot() == null
            || payment.getIdempotencyKey() == null || payment.getTotalAmount() == null) {
            payment.requireReview();
            outbox.requestPaymentSync(now, now);
            return Optional.empty();
        }
        boolean reconciliation = payment.getStatus() == PaymentsStatus.UNKNOWN;
        if (!reconciliation && (method.getStatus() != PaymentsMethodStatus.ACTIVE
            || !method.getMember().isActive()
            || !method.getMember().getId().equals(order.get().getMemberId())
            || order.get().getOrderStatus() != OrderStatus.PAYMENT_PENDING)) {
            payment.fail();
            order.get().setOrderStatus(OrderStatus.PAYMENT_FAILED);
            outbox.requestPaymentSync(now, now);
            return Optional.empty();
        }
        if (!reconciliation && order.get().getGroupBuy().getStatus()
            != GroupBuyStatus.RECRUITMENT_COMPLETED) {
            payment.fail();
            order.get().setOrderStatus(OrderStatus.PAYMENT_FAILED);
            outbox.requestPaymentSync(now, now);
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        payment.begin(token, instant, properties.getLease());
        return Optional.of(new Execution(paymentId, token, new AutomaticPaymentRequest(
            payment.getCustomerKeySnapshot(), method.getMethodKey(),
            payment.getMethod() == PaymentsMethod.CARD ? PaymentType.CARD : PaymentType.ACCOUNT,
            payment.getTotalAmount(), payment.getOrderNo(), payment.getOrderName()),
            payment.getIdempotencyKey(), reconciliation));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean allowReplay(Long paymentId, UUID token) {
        paymentsRepository.configureLockTimeout();
        Payments payment = paymentsRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || !payment.owns(token)) return false;
        Instant now = paymentsRepository.databaseNow();
        if (!payment.getProcessingDeadline().isAfter(now)
            || payment.getCreatedAt() == null
            || !now.isBefore(payment.getCreatedAt().atZone(ZONE_SEOUL).toInstant()
                .plus(properties.getReplayWindow()))
            || payment.getBrandPayMethod().getStatus() != PaymentsMethodStatus.ACTIVE
            || !payment.getBrandPayMethod().getMember().isActive()) {
            requireReview(payment, nowLocal());
            return false;
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long paymentId, UUID token, AutomaticPaymentResponse response) {
        paymentsRepository.configureLockTimeout();
        var orderId = paymentsRepository.findOrderId(paymentId);
        if (orderId.isEmpty() || ordersRepository.findByIdForPaymentUpdate(orderId.get()).isEmpty()) return;
        Payments payment = paymentsRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || !payment.owns(token)) return;
        if (response == null || !Objects.equals(payment.getOrderNo(), response.orderId())
            || !Objects.equals(payment.getOrderName(), response.orderName())
            || !Objects.equals(payment.getTotalAmount(), response.totalAmount())
            || !"DONE".equals(response.status()) || response.paymentKey() == null
            || response.paymentKey().isBlank() || response.approvedAt() == null) {
            requireReview(payment, nowLocal());
            return;
        }
        if (payment.getOrders().getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            requireReview(payment, nowLocal());
            return;
        }
        payment.completeBrandPay(response.paymentKey(), response.approvedAt()
            .atZoneSameInstant(ZONE_SEOUL).toLocalDateTime());
        payment.getOrders().setOrderStatus(OrderStatus.PAYMENT_COMPLETED);
        sync(paymentId, nowLocal());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleError(Long paymentId, UUID token, PaymentGatewayException error,
        boolean reconciliation) {
        paymentsRepository.configureLockTimeout();
        var orderId = paymentsRepository.findOrderId(paymentId);
        if (orderId.isEmpty() || ordersRepository.findByIdForPaymentUpdate(orderId.get()).isEmpty()) return;
        Payments payment = paymentsRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || !payment.owns(token)) return;
        log.warn("Payment execution error: paymentId={}, kind={}, code={}",
            paymentId, error.kind(), error.code());
        if (error.kind() == PaymentGatewayException.Kind.DECLINED && !reconciliation) {
            payment.fail();
            if (payment.getOrders().getOrderStatus() == OrderStatus.PAYMENT_PENDING) {
                payment.getOrders().setOrderStatus(OrderStatus.PAYMENT_FAILED);
            }
            sync(paymentId, nowLocal());
            return;
        }
        if (error.kind() == PaymentGatewayException.Kind.CONFIGURATION
            || error.kind() == PaymentGatewayException.Kind.INVALID_RESPONSE
            || reconciliation && error.kind() == PaymentGatewayException.Kind.DECLINED
            || payment.getAttemptCount() >= properties.getMaxAttempts()) {
            requireReview(payment, nowLocal());
            return;
        }
        long[] delays = {10, 30, 120, 600, 1800};
        long delay = delays[Math.min(payment.getAttemptCount() - 1, delays.length - 1)]
            + ThreadLocalRandom.current().nextLong(1, 6);
        payment.releaseForRetry();
        sync(paymentId, nowLocal().plusSeconds(delay));
    }

    private void requireReview(Payments payment, LocalDateTime now) {
        payment.requireReview();
        sync(payment.getId(), now);
    }

    private void sync(Long paymentId, LocalDateTime scheduledAt) {
        OutboxEvent event = outboxRepository.findByTypeAndAggregateIdForUpdate(
            OutboxEventType.PAYMENT_SCHEDULE_SYNC, paymentId)
            .orElseGet(() -> OutboxEvent.paymentScheduleSync(paymentId, scheduledAt, nowLocal()));
        event.requestPaymentSync(scheduledAt, nowLocal());
        outboxRepository.save(event);
    }

    private LocalDateTime nowLocal() {
        return LocalDateTime.ofInstant(paymentsRepository.databaseNow(), ZONE_SEOUL);
    }
}
