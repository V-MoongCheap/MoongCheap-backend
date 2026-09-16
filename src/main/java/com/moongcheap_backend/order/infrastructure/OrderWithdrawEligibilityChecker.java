package com.moongcheap_backend.order.infrastructure;

import com.moongcheap_backend.auth.infrastructure.port.WithdrawEligibilityChecker;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.order.domain.OrderStatus;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("orderWithdrawEligibilityChecker")
@RequiredArgsConstructor
public class OrderWithdrawEligibilityChecker implements WithdrawEligibilityChecker {

    private static final Set<OrderStatus> ACTIVE_STATUSES = Set.of(
        OrderStatus.PAYMENT_PENDING,
        OrderStatus.PAYMENT_COMPLETED,
        OrderStatus.PREPARING_SHIPMENT,
        OrderStatus.SHIPPED,
        OrderStatus.REFUND_PENDING
    );

    private final OrdersRepository ordersRepository;

    @Override
    public void ensureWithdrawable(Long memberId) {
        if (ordersRepository.existsByMemberIdAndOrderStatusIn(memberId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.WITHDRAW_BLOCKED_HAS_ORDER);
        }
    }
}
