package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.infrastructure.BrandPayPaymentClient.AutomaticPaymentResponse;
import java.util.Optional;

public interface PaymentReconciliationClient {
    Optional<AutomaticPaymentResponse> findByOrderId(String orderId);
}
