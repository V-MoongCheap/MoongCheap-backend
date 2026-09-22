package com.moongcheap_backend.order.infrastructure;

import com.moongcheap_backend.order.domain.OrderStatus;

public interface OrderStatusCount {

    OrderStatus getOrderStatus();

    long getCount();
}
