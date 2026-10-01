package com.moongcheap_backend.payout.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import com.moongcheap_backend.member.domain.Seller;
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

//판매자 식별키
@Entity
@Getter
@Table(name = "seller_key")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerKey extends BaseTimeEntity {

    @Id
    private Long sellerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @Column(name = "seller_key", nullable = false, unique = true, length = 20)
    private String sellerKey;

    public SellerKey(Seller seller, String sellerKey) {
        this.seller = seller;
        this.sellerKey = sellerKey;
    }
}
