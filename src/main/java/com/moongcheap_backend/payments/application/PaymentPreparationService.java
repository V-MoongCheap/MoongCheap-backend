package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토스 요청 전에 READY 결제 이력을 만들거나 기존 진행 이력을 재사용한다. */
@Service
@RequiredArgsConstructor
public class PaymentPreparationService {

    private static final Set<PaymentsStatus> REUSABLE_STATUSES = Set.of(
        PaymentsStatus.READY,
        PaymentsStatus.DONE
    );

    private final OrdersRepository ordersRepository;
    private final PaymentsRepository paymentsRepository;

    @Transactional
    public Preparation prepare(Long orderId, PaymentsMethod method) {
        // 같은 주문의 동시 준비 요청을 직렬화해 READY 레코드가 중복 생성되지 않게 한다.
        Orders order = ordersRepository.findByIdForPaymentUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getOrderStatus() == OrderStatus.PAYMENT_COMPLETED) {
            return Preparation.completed();
        }
        if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.BRAND_PAY_AUTO_PAYMENT_NOT_ALLOWED);
        }

        return paymentsRepository
            .findFirstByOrdersIdAndStatusInOrderByIdDesc(orderId, REUSABLE_STATUSES)
            .map(payment -> reuse(order, payment))
            .orElseGet(() -> create(order, method));
    }

    private Preparation reuse(Orders order, Payments payment) {
        if (payment.getStatus() == PaymentsStatus.DONE) {
            // 결제 저장 후 주문 상태만 유실된 과거 불일치도 이 시점에 복구한다.
            order.setOrderStatus(OrderStatus.PAYMENT_COMPLETED);
            return Preparation.completed();
        }
        return Preparation.requestRequired(payment.getId());
    }

    private Preparation create(Orders order, PaymentsMethod method) {
        Payments payment = paymentsRepository.saveAndFlush(Payments.readyBrandPay(
            order,
            order.getOrderNo(),
            order.getProductName(),
            order.getTotalAmount(),
            method
        ));
        return Preparation.requestRequired(payment.getId());
    }

    public record Preparation(Long paymentId, boolean requestRequired) {

        private static Preparation completed() {
            return new Preparation(null, false);
        }

        private static Preparation requestRequired(Long paymentId) {
            return new Preparation(paymentId, true);
        }
    }
}
