package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.order.infrastructure.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 성사 판정이 커밋된 공동구매의 주문을 결제 Outbox 예약으로 연결한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPaymentReservationService {
    private final OrdersRepository ordersRepository;
    private final PaymentPreparationService preparationService;
    private final PaymentQueueProperties properties;

    public int scheduleForGroup(Long groupBuyId) {
        if (!properties.isEnabled()) return 0;
        int scheduled = 0;
        long cursor = 0;
        while (true) {
            var orderIds = ordersRepository.findUnscheduledPaymentOrderIdsByGroupBuy(
                groupBuyId, cursor, properties.getBatchSize());
            if (orderIds.isEmpty()) return scheduled;
            for (Long orderId : orderIds) {
                cursor = orderId;
                try {
                    preparationService.schedule(orderId);
                    scheduled++;
                } catch (RuntimeException exception) {
                    // 한 주문의 데이터 문제나 일시 장애가 다른 주문 예약을 막지 않는다.
                    log.warn("Payment reservation deferred after judgment: groupBuyId={}, orderId={}",
                        groupBuyId, orderId);
                }
            }
            if (orderIds.size() < properties.getBatchSize()) return scheduled;
        }
    }
}
