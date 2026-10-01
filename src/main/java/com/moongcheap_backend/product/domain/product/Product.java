package com.moongcheap_backend.product.domain.product;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_id", nullable = false)
    private Long catalogId;

    @Column(name = "demand_board_id", nullable = false)
    private Long demandBoardId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "thumbnail_url", length = 255)
    private String thumbnailUrl;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @Column(name = "shipping_fee", nullable = false)
    private Integer shippingFee = 0;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "sale_end_at", nullable = false)
    private LocalDateTime saleEndAt;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "min_participant_count", nullable = false)
    private Integer minParticipantCount;

    @Column(name = "min_quantity", nullable = false)
    private Integer minQuantity;

    @Column(name = "max_quantity_per_member")
    private Integer maxQuantityPerMember;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "return_policy", nullable = false, columnDefinition = "TEXT")
    private String returnPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.BIDDING;

    @Column(name = "awarded_at")
    private LocalDateTime awardedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder
    private Product(Long catalogId, Long demandBoardId, Long sellerId, String thumbnailUrl,
        Integer unitPrice, Integer shippingFee, LocalDate deliveryDate, LocalDateTime saleEndAt,
        Integer totalQuantity, Integer minParticipantCount, Integer minQuantity,
        Integer maxQuantityPerMember, String description, String returnPolicy) {
        this.catalogId = catalogId;
        this.demandBoardId = demandBoardId;
        this.sellerId = sellerId;
        this.thumbnailUrl = thumbnailUrl;
        this.unitPrice = unitPrice;
        this.shippingFee = shippingFee != null ? shippingFee : 0;
        this.deliveryDate = deliveryDate;
        this.saleEndAt = saleEndAt;
        this.totalQuantity = totalQuantity;
        this.minParticipantCount = minParticipantCount;
        this.minQuantity = minQuantity;
        this.maxQuantityPerMember = maxQuantityPerMember;
        this.description = description;
        this.returnPolicy = returnPolicy;
    }

    public boolean isAwarded() {
        return status == ProductStatus.AWARDED;
    }

    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public void startSale() {
        this.status = ProductStatus.ON_SALE;
    }
}
