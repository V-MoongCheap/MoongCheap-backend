package com.moongcheap_backend.demand.domain.rejectHistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(name = "reject_history")
@IdClass(RejectHistoryId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RejectHistory {

    @Id
    @Column(name = "demand_id", nullable = false)
    private Long demandId;

    @Id
    @Column(name = "demand_board_id", nullable = false)
    private Long demandBoardId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RejectHistory of(Long demandId, Long demandBoardId) {
        RejectHistory history = new RejectHistory();
        history.demandId = demandId;
        history.demandBoardId = demandBoardId;
        return history;
    }
}
