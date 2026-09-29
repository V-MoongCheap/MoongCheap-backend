package com.moongcheap_backend.payments.infrastructure;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 단일 인스턴스 부하테스트용. 승인 이력은 재시작 시 소멸한다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "moongcheap.payments.gateway.mode", havingValue = "mock")
public class MockBrandPayPaymentClient implements BrandPayPaymentClient,
    PaymentReconciliationClient, PaymentCancellationClient {

    private record Approval(String idempotencyKey, AutomaticPaymentRequest request,
                            AutomaticPaymentResponse response) { }

    private final PaymentGatewayProperties properties;
    private final Map<String, Approval> byKey = new HashMap<>();
    private final Map<String, Approval> byOrder = new HashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public MockBrandPayPaymentClient(PaymentGatewayProperties properties) {
        properties.validate();
        this.properties = properties;
        log.warn("MOCK payment gateway enabled: delay={}, approvals are in-memory only",
            properties.getMockDelay());
    }

    @Override
    public AutomaticPaymentResponse pay(AutomaticPaymentRequest request, String idempotencyKey) {
        calls.incrementAndGet();
        delay();
        if (request == null || request.orderId() == null || request.orderId().isBlank()
            || request.orderName() == null || request.amount() <= 0
            || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentGatewayException(PaymentGatewayException.Kind.INVALID_RESPONSE,
                "MOCK_INVALID_REQUEST");
        }
        // 지연은 잠금 밖에서 수행해 병렬 Worker를 직렬화하지 않는다.
        synchronized (this) {
            Approval existing = byKey.get(idempotencyKey);
            if (existing == null) existing = byOrder.get(request.orderId());
            if (existing != null) {
                if (!existing.idempotencyKey().equals(idempotencyKey)
                    || !existing.request().equals(request)) {
                    throw new PaymentGatewayException(PaymentGatewayException.Kind.INVALID_RESPONSE,
                        "MOCK_IDEMPOTENCY_CONFLICT");
                }
                return existing.response();
            }
            var response = new AutomaticPaymentResponse("mock_" + UUID.randomUUID(),
                request.orderId(), request.orderName(), request.amount(), "DONE", OffsetDateTime.now());
            var approval = new Approval(idempotencyKey, request, response);
            byKey.put(idempotencyKey, approval);
            byOrder.put(request.orderId(), approval);
            return response;
        }
    }

    @Override
    public Optional<AutomaticPaymentResponse> findByOrderId(String orderId) {
        delay();
        synchronized (this) {
            return Optional.ofNullable(byOrder.get(orderId)).map(Approval::response);
        }
    }

    @Override
    public CancellationResponse cancel(String paymentKey, String cancelReason, String idempotencyKey) {
        // 취소는 이번 부하테스트 범위 밖이며 실제 Toss로 전달하지 않는다.
        throw new PaymentGatewayException(PaymentGatewayException.Kind.CONFIGURATION,
            "MOCK_CANCELLATION_UNSUPPORTED");
    }

    public long paymentCallCount() { return calls.get(); }

    /** 고객키·결제수단키를 포함하지 않는 대사용 승인 스냅샷. */
    public synchronized Map<String, AutomaticPaymentResponse> approvals() {
        Map<String, AutomaticPaymentResponse> snapshot = new HashMap<>();
        byOrder.forEach((orderId, approval) -> snapshot.put(orderId, approval.response()));
        return Map.copyOf(snapshot);
    }

    private void delay() {
        try {
            Thread.sleep(properties.getMockDelay());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException(PaymentGatewayException.Kind.UNKNOWN,
                "MOCK_INTERRUPTED");
        }
    }
}
