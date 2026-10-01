package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.payments.domain.BrandPayMethod;
import com.moongcheap_backend.payments.presentation.dto.OrderPaymentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

//다른도메인에서 payment의 정보가 필요할 때 사용
@Service
@RequiredArgsConstructor
public class PaymentPublicService {

    public OrderPaymentInfo getForOrder(Orders order) {
        return new OrderPaymentInfo(
            order.getPrice() * order.getSum(),
            order.getDeliveryFee(),
            order.getTotalAmount(),
            formatPaymentMethod(order.getBrandPayMethod())
        );
    }

    private String formatPaymentMethod(BrandPayMethod paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }

        return paymentMethod.getProviderCode() + " " + paymentMethod.getMaskedNumber();
    }
}
