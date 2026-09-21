package com.moongcheap_backend.payments.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;
import com.moongcheap_backend.payments.domain.enums.ProviderCode;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "brand_pay_method")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandPayMethod extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    //자동결제키
    @Column(name = "method_key", nullable = false, unique = true)
    private String methodKey;

    //은행/카드사 코드
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 30)
    private ProviderCode providerCode;

    //계좌/카드번호
    @Column(name = "masked_number", nullable = false, length = 30)
    private String maskedNumber;

    //결제수단 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PaymentType type;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentsMethodStatus status;

    /** 토스에서 처음 확인된 결제수단을 로컬 자동결제 수단으로 생성한다. */
    public BrandPayMethod(Member member, String methodKey, ProviderCode providerCode,
        String maskedNumber, PaymentType type, boolean isDefault) {
        this.member = member;
        this.methodKey = methodKey;
        this.providerCode = providerCode;
        this.maskedNumber = maskedNumber;
        this.type = type;
        this.isDefault = isDefault;
        this.status = PaymentsMethodStatus.ACTIVE;
    }

    /** 토스의 최신 조회 결과로 기존 결제수단 표시 정보와 활성 상태를 갱신한다. */
    public void synchronize(ProviderCode providerCode, String maskedNumber,
        PaymentType type, boolean isDefault) {
        this.providerCode = providerCode;
        this.maskedNumber = maskedNumber;
        this.type = type;
        this.isDefault = isDefault;
        this.status = PaymentsMethodStatus.ACTIVE;
    }

    /** 토스의 전체 활성 결제수단 응답에서 사라진 수단을 물리 삭제하지 않고 만료 처리한다. */
    public void expire() {
        this.isDefault = false;
        this.status = PaymentsMethodStatus.EXPIRED;
    }
}
