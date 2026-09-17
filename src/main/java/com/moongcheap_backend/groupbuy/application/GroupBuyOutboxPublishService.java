package com.moongcheap_backend.groupbuy.application;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyOutboxPublishService {

    private final OutboxEventRepository outboxEventRepository;
    private final GroupBuyJudgmentSchedule judgmentSchedule;
    private final GroupBuyOrderCreationStream orderCreationStream;

    // Outbox 행 잠금부터 발행 결과 기록까지 하나의 DB 트랜잭션으로 처리한다.
    @Transactional
    public int publishBatch(LocalDateTime now, int batchSize) {
        List<OutboxEvent> events = outboxEventRepository.findPublishableForUpdate(now, batchSize);

        for (OutboxEvent event : events) {
            try {
                // 외부 발행 성공 후에만 완료 처리한다. 중복 발행은 소비자의 멱등 처리로 흡수한다.
                publish(event);
                event.markPublished(now);
            } catch (RuntimeException exception) {
                // Redis 장애가 DB 트랜잭션 전체를 롤백시키지 않도록 다음 시각에 재시도한다.
                event.scheduleRetry(now);
                log.warn(
                    "Failed to publish outbox event: id={}, type={}, retryCount={}",
                    event.getId(), event.getEventType(), event.getRetryCount(), exception
                );
            }
        }

        return events.size();
    }

    // 이벤트 종류별 외부 발행 처리를 한곳에서 분기한다.
    private void publish(OutboxEvent event) {
        switch (event.getEventType()) {
            case OutboxEventType.GROUP_BUY_ORDER_CREATION_REQUESTED ->
                orderCreationStream.publish(event.getId(), event.getAggregateId());
            case OutboxEventType.GROUP_BUY_JUDGMENT_SCHEDULED ->
                judgmentSchedule.schedule(event.getAggregateId(), event.getScheduledAt());
        }
    }
}
