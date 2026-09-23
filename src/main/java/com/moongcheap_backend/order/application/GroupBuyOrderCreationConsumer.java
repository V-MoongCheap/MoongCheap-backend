package com.moongcheap_backend.order.application;

import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyOrderCreationConsumer {

    private static final int BATCH_SIZE = 20;
    private static final Duration PENDING_MIN_IDLE_TIME = Duration.ofSeconds(30);

    private final GroupBuyOrderCreationStream orderCreationStream;
    private final OrderService orderService;
    private final String consumerName = "order-creator-" + UUID.randomUUID();

    // 새 메시지와 처리 중 워커가 종료되어 남은 메시지를 함께 소비한다.
    @Scheduled(fixedDelayString = "${moongcheap.group-buy.order-consume-delay-ms}")
    public void consume() {
        try {
            process(orderCreationStream.claimStale(
                consumerName,
                BATCH_SIZE,
                PENDING_MIN_IDLE_TIME
            ));
            process(orderCreationStream.readNewMessage(consumerName, BATCH_SIZE));
        } catch (RuntimeException exception) {
            // Redis 조회 장애는 다음 스케줄에서 다시 시도한다.
            log.warn("Failed to consume group-buy order creation stream", exception);
        }
    }

    private void process(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            Long groupBuyId;
            try {
                groupBuyId = orderCreationStream.getGroupBuyId(record);
            } catch (IllegalArgumentException exception) {
                // 필수 값이 없는 메시지는 재처리해도 성공할 수 없으므로 ACK 후 삭제한다.
                log.warn("Discarding invalid group-buy order creation message: id={}",
                    record.getId(), exception);
                orderCreationStream.acknowledgeAndDelete(record);
                continue;
            }

            try {
                // 별도 트랜잭션의 주문 생성 커밋이 끝난 후에만 ACK 후 삭제한다.
                orderService.autoCreateOrder(groupBuyId);
                orderCreationStream.acknowledgeAndDelete(record);
            } catch (RuntimeException exception) {
                // ACK하지 않은 메시지는 Pending에 남고 유휴 시간이 지나면 다시 회수된다.
                log.warn("Group-buy order creation failed: id={}", record.getId(), exception);
            }
        }
    }
}
