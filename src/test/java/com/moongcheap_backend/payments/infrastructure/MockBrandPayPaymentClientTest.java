package com.moongcheap_backend.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.payments.domain.enums.PaymentType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class MockBrandPayPaymentClientTest {
    private MockBrandPayPaymentClient client() {
        var properties = new PaymentGatewayProperties();
        properties.setMockDelay(Duration.ZERO);
        return new MockBrandPayPaymentClient(properties);
    }

    private BrandPayPaymentClient.AutomaticPaymentRequest request(int amount) {
        return new BrandPayPaymentClient.AutomaticPaymentRequest(
            "customer", "method", PaymentType.CARD, amount, "order-1", "test order");
    }

    @Test void concurrentReplayCreatesOneApprovalAndLookupReturnsIt() throws Exception {
        var client = client();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<BrandPayPaymentClient.AutomaticPaymentResponse>>();
            for (int i = 0; i < 40; i++) {
                results.add(executor.submit(() -> client.pay(request(1000), "same-key")));
            }
            var expected = results.getFirst().get();
            for (var result : results) assertThat(result.get()).isEqualTo(expected);
            assertThat(client.approvals()).hasSize(1);
            assertThat(client.paymentCallCount()).isEqualTo(40);
            assertThat(client.findByOrderId("order-1")).contains(expected);
            assertThat(client.findByOrderId("missing")).isEmpty();
            assertThat(expected.status()).isEqualTo("DONE");
            assertThat(expected.totalAmount()).isEqualTo(1000);
        }
    }

    @Test void changedAmountOrKeyDoesNotCreateAnotherApproval() {
        var client = client();
        client.pay(request(1000), "same-key");
        assertThatThrownBy(() -> client.pay(request(2000), "same-key"))
            .isInstanceOf(PaymentGatewayException.class).hasMessage("MOCK_IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> client.pay(request(1000), "other-key"))
            .isInstanceOf(PaymentGatewayException.class).hasMessage("MOCK_IDEMPOTENCY_CONFLICT");
        assertThat(client.approvals()).hasSize(1);
    }

    @Test void interruptionPreservesInterruptAndDoesNotApprove() {
        var client = client();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.pay(request(1000), "key"))
                .isInstanceOf(PaymentGatewayException.class).hasMessage("MOCK_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(client.approvals()).isEmpty();
        } finally {
            Thread.interrupted();
        }
    }

    @Test void cancellationIsExplicitlyUnsupported() {
        assertThatThrownBy(() -> client().cancel("key", "reason", "idempotency"))
            .isInstanceOf(PaymentGatewayException.class).hasMessage("MOCK_CANCELLATION_UNSUPPORTED");
    }
}
