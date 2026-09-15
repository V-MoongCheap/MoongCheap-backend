package com.moongcheap_backend.product.domain.productAwardEvaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_award_evaluation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAwardEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_award_evaluation_seq")
    @SequenceGenerator(
        name = "product_award_evaluation_seq",
        sequenceName = "product_award_evaluation_id_seq",
        allocationSize = 20
    )
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "demand_board_id", nullable = false)
    private Long demandBoardId;

    @Column(name = "score", precision = 5, scale = 4)
    private BigDecimal score;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "is_awarded", nullable = false)
    private boolean isAwarded;

    @Column(name = "judged_at", nullable = false)
    private LocalDateTime judgedAt;

    @Builder
    private ProductAwardEvaluation(Long productId, Long demandBoardId, BigDecimal score,
        String reason, boolean isAwarded, LocalDateTime judgedAt) {
        this.productId = productId;
        this.demandBoardId = demandBoardId;
        this.score = score;
        this.reason = reason;
        this.isAwarded = isAwarded;
        this.judgedAt = judgedAt;
    }
}
