package com.moongcheap_backend.payments.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import com.moongcheap_backend.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

//소비자 식별키
@Entity
@Getter
@Table(name = "customer_key")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerKey extends BaseTimeEntity {

    @Id
    private Long memberId; // member_id를 PK로 사용

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Member의 PK(id)를 자신의 PK(memberId)로 매핑
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "customer_key", nullable = false, unique = true, length = 50)
    private String customerKey;

    public CustomerKey(Member member, String customerKey) {
        this.member = member;
        this.customerKey = customerKey;
    }
}
