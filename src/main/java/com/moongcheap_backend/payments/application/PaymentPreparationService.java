package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.infrastructure.CustomerKeyRepository;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 결제와 Redis 전달용 Outbox만 저장하며 외부 시스템은 호출하지 않는다. */
@Service
@RequiredArgsConstructor
public class PaymentPreparationService {
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final OrdersRepository ordersRepository;
    private final PaymentsRepository paymentsRepository;
    private final CustomerKeyRepository customerKeyRepository;
    private final OutboxEventRepository outboxRepository;
    private final BrandPayIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long schedule(Long orderId) {
        paymentsRepository.configureLockTimeout();
        Orders order = ordersRepository.findByIdForPaymentUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        var previous = paymentsRepository.findFirstByOrdersIdOrderByIdDesc(orderId);
        if (previous.isPresent()) return previous.get().getId();

        BrandPayMethod method = order.getBrandPayMethod();
        var customer = customerKeyRepository.findById(order.getMemberId());
        boolean valid = order.getOrderStatus() == OrderStatus.PAYMENT_PENDING
            && order.getGroupBuy().getStatus() == GroupBuyStatus.RECRUITMENT_COMPLETED
            && method != null && method.getStatus() == PaymentsMethodStatus.ACTIVE
            && method.getMember().getId().equals(order.getMemberId())
            && method.getMember().isActive()
            && customer.isPresent() && customer.get().getCustomerKey() != null
            && !customer.get().getCustomerKey().isBlank()
            && order.getTotalAmount() != null && order.getTotalAmount() > 0
            && order.getProductName() != null && !order.getProductName().isBlank()
            && order.getProductName().length() <= 100;

        Payments payment = Payments.readyBrandPay(order, order.getOrderNo(),
            order.getProductName(), order.getTotalAmount(),
            method != null && method.getType() == PaymentType.CARD
                ? PaymentsMethod.CARD : PaymentsMethod.TRANSFER);
        if (valid) {
            payment.schedule(method, customer.get().getCustomerKey(),
                idempotencyKeyGenerator.forAutomaticPayment(order.getOrderNo()));
        } else {
            payment.fail();
            order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
        }

        paymentsRepository.saveAndFlush(payment);
        LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
        outboxRepository.save(OutboxEvent.paymentScheduleSync(payment.getId(), now, now));
        return payment.getId();
    }
}
