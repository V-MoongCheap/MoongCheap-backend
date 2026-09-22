package com.moongcheap_backend.payments.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import com.moongcheap_backend.order.domain.Orders;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethod;
import com.moongcheap_backend.payments.domain.enums.PaymentsStatus;
import com.moongcheap_backend.payments.domain.enums.PaymentsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payments extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders orders;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(name = "order_no", length = 64)
    private String orderNo;

    @Column(name = "order_name")
    private String orderName;

    @Column(name = "total_amount")
    private Integer totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payments_status", nullable = false, length = 30)
    private PaymentsStatus status = PaymentsStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payments_type", length = 30)
    private PaymentsType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 30)
    private PaymentsMethod method;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb")
    private String detail;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "processing_deadline")
    private Instant processingDeadline;

    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_pay_method_id")
    private BrandPayMethod brandPayMethod;

    @Column(name = "customer_key_snapshot", length = 50)
    private String customerKeySnapshot;

    /** 토스 자동결제 요청 전에 추적 가능한 READY 결제 이력을 생성한다. */
    public static Payments readyBrandPay(
        Orders order,
        String orderNo,
        String orderName,
        Integer totalAmount,
        PaymentsMethod method
    ) {
        Payments payment = new Payments();
        payment.orders = order;
        payment.orderNo = orderNo;
        payment.orderName = orderName;
        payment.totalAmount = totalAmount;
        payment.status = PaymentsStatus.PENDING;
        payment.type = PaymentsType.BRANDPAY;
        payment.method = method;
        return payment;
    }

    public void schedule(BrandPayMethod brandPayMethod, String customerKey,
        String idempotencyKey) {
        this.brandPayMethod = brandPayMethod;
        this.customerKeySnapshot = customerKey;
        this.idempotencyKey = idempotencyKey;
    }

    public boolean isAutomaticallyExecutable() {
        return status == PaymentsStatus.PENDING || status == PaymentsStatus.UNKNOWN;
    }

    public boolean owns(UUID token) {
        return token != null && token.equals(processingToken) && isAutomaticallyExecutable();
    }

    public void begin(UUID token, Instant now, Duration lease) {
        if (!isAutomaticallyExecutable()) {
            throw new IllegalStateException("자동 실행 가능한 결제가 아닙니다.");
        }
        processingToken = token;
        processingDeadline = now.plus(lease);
        attemptCount++;
        status = PaymentsStatus.UNKNOWN;
    }

    public void releaseForRetry() {
        processingToken = null;
        processingDeadline = null;
    }

    public void requireReview() {
        status = PaymentsStatus.REVIEW_REQUIRED;
        releaseForRetry();
    }

    public void fail() {
        status = PaymentsStatus.FAILED;
        releaseForRetry();
    }

    /** 토스 승인 성공 정보를 기존 READY 결제 이력에 반영한다. */
    public void completeBrandPay(String paymentKey, LocalDateTime approvedAt) {
        if (status == PaymentsStatus.SUCCEEDED) {
            return;
        }
        if (!isAutomaticallyExecutable()) {
            throw new IllegalStateException("실행 가능한 결제만 완료할 수 있습니다.");
        }
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.status = PaymentsStatus.SUCCEEDED;
        releaseForRetry();
    }

    /** 토스의 전액 취소 성공 응답을 반영한다. */
    public void cancel(String cancelReason, LocalDateTime canceledAt) {
        if (status == PaymentsStatus.CANCELED) {
            return;
        }
        if (status != PaymentsStatus.SUCCEEDED) {
            throw new IllegalStateException("성공한 결제만 취소할 수 있습니다.");
        }
        this.cancelReason = cancelReason;
        this.canceledAt = canceledAt;
        this.status = PaymentsStatus.CANCELED;
    }
}
