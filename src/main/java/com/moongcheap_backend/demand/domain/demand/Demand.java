package com.moongcheap_backend.demand.domain.demand;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "demand")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Demand extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "demand_board_id")
    private Long demandBoardId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "catalog_id", nullable = false)
    private Long catalogId;

    @Column(name = "desired_price_min", nullable = false)
    private Integer desiredPriceMin;

    @Column(name = "desired_price_max", nullable = false)
    private Integer desiredPriceMax;

    @Column(name = "desire_end_at", nullable = false)
    private LocalDateTime desireEndAt;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "extra_requirement", length = 200)
    private String extraRequirement;

    @Column(name = "is_substitutable", nullable = false)
    private boolean isSubstitutable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DemandStatus status = DemandStatus.UNASSIGNED;

    @Column(name = "pay_method_id")
    private Long payMethodId;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public void cancel() {
        this.status = DemandStatus.CANCELED;
    }

    @Builder
    private Demand(Long memberId, Long catalogId, Long payMethodId, Integer desiredPriceMin,
        Integer desiredPriceMax,
        LocalDateTime desireEndAt, Integer quantity, String extraRequirement,
        boolean isSubstitutable) {
        this.memberId = memberId;
        this.catalogId = catalogId;
        this.payMethodId = payMethodId;
        this.desiredPriceMin = desiredPriceMin;

        this.desiredPriceMax = desiredPriceMax;
        this.desireEndAt = desireEndAt;
        this.quantity = quantity;
        this.extraRequirement = extraRequirement;
        this.isSubstitutable = isSubstitutable;
    }

    @Builder(builderMethodName = "boardJoinBuilder")
    private Demand(Long memberId, Long catalogId, Long demandBoardId, Long payMethodId,
        Integer desiredPriceMin, Integer desiredPriceMax, LocalDateTime desireEndAt,
        Integer quantity, boolean isSubstitutable, String extraRequirement) {
        this.memberId = memberId;
        this.catalogId = catalogId;
        this.demandBoardId = demandBoardId;
        this.payMethodId = payMethodId;
        this.desiredPriceMin = desiredPriceMin;
        this.desiredPriceMax = desiredPriceMax;
        this.desireEndAt = desireEndAt;
        this.quantity = quantity;
        this.isSubstitutable = isSubstitutable;
        this.extraRequirement = extraRequirement;
        this.status = DemandStatus.ASSIGNED;
    }

    public void acceptOffer() {
        this.status = DemandStatus.ASSIGNED;
    }

    public void rejectOffer() {
        this.demandBoardId = null;
        this.status = DemandStatus.UNASSIGNED;
    }

    public void substituteOffer(Long demandBoardId) {
        this.demandBoardId = demandBoardId;
        this.status = DemandStatus.SUBSTITUTE_OFFERED;
    }
}
