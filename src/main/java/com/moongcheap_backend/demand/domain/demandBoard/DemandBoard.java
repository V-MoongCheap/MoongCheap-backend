package com.moongcheap_backend.demand.domain.demandBoard;

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
@Table(name = "demand_board")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemandBoard extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_id", nullable = false)
    private Long catalogId;

    @Column(name = "participant_count", nullable = false)
    private int participantCount = 0;

    @Column(name = "price_min")
    private Integer priceMin;

    @Column(name = "price_max")
    private Integer priceMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DemandBoardStatus status = DemandBoardStatus.GB_GATHERING;

    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    @Column(name = "sale_end_at", nullable = false)
    private LocalDateTime saleEndAt;

    @Builder
    private DemandBoard(Long catalogId, Integer priceMin, Integer priceMax,
        LocalDateTime saleEndAt, int participantCount, DemandBoardStatus status) {
        this.catalogId = catalogId;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.saleEndAt = saleEndAt;
        this.participantCount = participantCount;
        this.status = status;
    }

    public void increaseParticipantCount() {
        this.participantCount += 1;
    }

    public void increaseParticipantCount(int count) {
        this.participantCount += count;
    }
}
