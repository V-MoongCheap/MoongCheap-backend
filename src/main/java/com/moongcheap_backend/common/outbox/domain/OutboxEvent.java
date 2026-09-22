package com.moongcheap_backend.common.outbox.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "outbox_event",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_outbox_event_type_aggregate_id",
        columnNames = {"event_type", "aggregate_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseTimeEntity {

    // Redis 장애가 길어져도 재시도 간격은 최대 5분으로 제한한다.
    private static final int MAX_RETRY_DELAY_SECONDS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private OutboxEventType eventType;

    // 이벤트 종류에 따른 aggregate ID(groupBuyId 또는 paymentId)
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    // Redis Sorted Set의 score로 사용할 실제 판정 예정 시각
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    //Outbox를 Redis에 등록하려는 다음 시각
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private OutboxEvent(
        OutboxEventType eventType,
        Long aggregateId,
        LocalDateTime scheduledAt,
        LocalDateTime nextAttemptAt
    ) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.scheduledAt = scheduledAt;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = nextAttemptAt;
    }

    // GroupBuy와 같은 DB 트랜잭션에서 생성해 Redis 등록 요청의 유실을 막는다.
    public static OutboxEvent groupBuyJudgmentScheduled(
        Long groupBuyId,
        LocalDateTime scheduledAt,
        LocalDateTime nextAttemptAt
    ) {
        return new OutboxEvent(
            OutboxEventType.GROUP_BUY_JUDGMENT_SCHEDULED,
            groupBuyId,
            scheduledAt,
            nextAttemptAt
        );
    }

    // GroupBuy 생성과 같은 DB 트랜잭션에서 생성해 주문 생성 요청의 유실을 막는다.
    public static OutboxEvent groupBuyOrderCreationRequested(
        Long groupBuyId,
        LocalDateTime now
    ) {
        return new OutboxEvent(
            OutboxEventType.GROUP_BUY_ORDER_CREATION_REQUESTED,
            groupBuyId,
            now,
            now
        );
    }

    public static OutboxEvent paymentScheduleSync(
        Long paymentId,
        LocalDateTime scheduledAt,
        LocalDateTime now
    ) {
        return new OutboxEvent(
            OutboxEventType.PAYMENT_SCHEDULE_SYNC,
            paymentId,
            scheduledAt,
            now
        );
    }

    /** 최신 결제 예약 또는 삭제 의도를 Redis에 다시 반영하도록 만든다. */
    public void requestPaymentSync(LocalDateTime scheduledAt, LocalDateTime now) {
        if (eventType != OutboxEventType.PAYMENT_SCHEDULE_SYNC) {
            throw new IllegalStateException("결제 Outbox만 재예약할 수 있습니다.");
        }
        this.scheduledAt = scheduledAt;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = now;
        this.publishedAt = null;
    }

    public void markPublished(LocalDateTime now) {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = now;
    }

    // 발행 실패 이벤트는 PENDING으로 유지하고 지수 백오프로 다음 시도를 늦춘다.
    public void scheduleRetry(LocalDateTime now) {
        retryCount++;
        long delaySeconds = Math.min(1L << Math.min(retryCount, 8), MAX_RETRY_DELAY_SECONDS);
        nextAttemptAt = now.plusSeconds(delaySeconds);
    }
}
