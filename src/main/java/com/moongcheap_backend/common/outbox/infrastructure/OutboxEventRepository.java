package com.moongcheap_backend.common.outbox.infrastructure;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 다중 인스턴스가 서로 다른 이벤트를 발행하도록 잠긴 행은 건너뛴다.
    @Query(value = """
        SELECT *
        FROM outbox_event
        WHERE status = 'PENDING'
          AND next_attempt_at <= :now
        ORDER BY id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPublishableForUpdate(
        @Param("now") LocalDateTime now,
        @Param("batchSize") int batchSize
    );
}
