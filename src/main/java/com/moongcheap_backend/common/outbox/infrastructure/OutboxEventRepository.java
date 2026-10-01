package com.moongcheap_backend.common.outbox.infrastructure;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 다중 인스턴스가 서로 다른 이벤트를 발행하도록 잠긴 행은 건너뛴다.
    @Query(value = """
        SELECT *
        FROM outbox_event
        WHERE status = 'PENDING'
          AND event_type IN ('GROUP_BUY_JUDGMENT_SCHEDULED', 'GROUP_BUY_ORDER_CREATION_REQUESTED')
          AND next_attempt_at <= :now
        ORDER BY id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPublishableForUpdate(
        @Param("now") LocalDateTime now,
        @Param("batchSize") int batchSize
    );

    @Query("""
        select e.id from OutboxEvent e
        where e.eventType = :type and e.status = com.moongcheap_backend.common.outbox.domain.OutboxEventStatus.PENDING
          and e.nextAttemptAt <= :now
        order by e.id
        """)
    List<Long> findPublishableIds(@Param("type") OutboxEventType type,
        @Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    Optional<OutboxEvent> findByEventTypeAndAggregateId(OutboxEventType type, Long aggregateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.eventType = :type and e.aggregateId = :aggregateId")
    Optional<OutboxEvent> findByTypeAndAggregateIdForUpdate(@Param("type") OutboxEventType type,
        @Param("aggregateId") Long aggregateId);

    @Query("""
        select e.id from OutboxEvent e
        where e.eventType = :type and e.id > :afterId
        order by e.id
        """)
    List<Long> findIdsForRecovery(@Param("type") OutboxEventType type,
        @Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);
}
