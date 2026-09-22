package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.domain.OrderStatus;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import com.moongcheap_backend.payments.infrastructure.PaymentsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토스 승인 성공 결과와 주문 상태를 하나의 짧은 DB 트랜잭션으로 반영한다. */
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private final OrdersRepository ordersRepository;
    private final PaymentsRepository paymentsRepository;

    @Transactional
    public void completeAutomaticPayment(Long orderId, Long paymentId,
        AutomaticPaymentResponse response) {
        // 같은 주문의 동시 완료 요청은 행 잠금으로 직렬화한다.
        Orders order = ordersRepository.findByIdForPaymentUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Payments payment = paymentsRepository.findByIdForUpdate(paymentId)
            .filter(found -> found.getOrders().getId().equals(orderId))
            .orElseThrow(() -> new BusinessException(
                ErrorCode.BRAND_PAY_AUTO_PAYMENT_NOT_ALLOWED));

        if (order.getOrderStatus() == OrderStatus.PAYMENT_COMPLETED
            || payment.getStatus() == PaymentsStatus.DONE) {
            return;
        }
        if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.BRAND_PAY_AUTO_PAYMENT_NOT_ALLOWED);
        }

        payment.completeBrandPay(
            response.paymentKey(),
            response.approvedAt().toLocalDateTime()
        );
        order.setOrderStatus(OrderStatus.PAYMENT_COMPLETED);
    }
}
