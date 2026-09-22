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
    private PaymentsStatus status = PaymentsStatus.READY;

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
        payment.status = PaymentsStatus.READY;
        payment.type = PaymentsType.BRANDPAY;
        payment.method = method;
        return payment;
    }

    /** 토스 승인 성공 정보를 기존 READY 결제 이력에 반영한다. */
    public void completeBrandPay(String paymentKey, LocalDateTime approvedAt) {
        if (status == PaymentsStatus.DONE) {
            return;
        }
        if (status != PaymentsStatus.READY) {
            throw new IllegalStateException("READY 상태의 결제만 완료할 수 있습니다.");
        }
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.status = PaymentsStatus.DONE;
    }
}
