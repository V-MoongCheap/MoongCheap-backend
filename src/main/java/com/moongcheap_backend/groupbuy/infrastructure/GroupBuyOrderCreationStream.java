package com.moongcheap_backend.groupbuy.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupBuyOrderCreationStream {

    private static final String KEY = "moongcheap:group-buy:order-creation";
    private static final String CONSUMER_GROUP = "group-buy-order-creators";
    private static final String FIELD_OUTBOX_EVENT_ID = "outboxEventId";
    private static final String FIELD_GROUP_BUY_ID = "groupBuyId";
    private static final DefaultRedisScript<Long> ACKNOWLEDGE_AND_DELETE_SCRIPT =
        new DefaultRedisScript<>("""
            local acknowledged = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            if acknowledged == 0 then
                return 0
            end
            return redis.call('XDEL', KEYS[1], ARGV[2])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AtomicBoolean consumerGroupReady = new AtomicBoolean();

    // 전용 Stream이므로 메시지에는 추적용 Outbox ID와 처리 대상 GroupBuy ID만 저장한다.
    public void publish(Long outboxEventId, Long groupBuyId) {
        redisTemplate.opsForStream().add(KEY, Map.of(
            FIELD_OUTBOX_EVENT_ID, outboxEventId.toString(),
            FIELD_GROUP_BUY_ID, groupBuyId.toString()
        ));
    }

    //새 메시지를 읽는 메서드
    public List<MapRecord<String, Object, Object>> readNewMessage(String consumerName,
        int batchSize) {
        List<MapRecord<String, Object, Object>> records = executeWithConsumerGroupRetry(() ->
            redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, consumerName),
                StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(KEY, ReadOffset.lastConsumed())
            )
        );
        return records == null ? List.of() : records;
    }

    // 처리 중 워커가 종료된 메시지를 다른 워커가 다시 가져갈 수 있게 한다.
    public List<MapRecord<String, Object, Object>> claimStale(
        String consumerName,
        int batchSize,
        Duration minimumIdleTime
    ) {
        //오래된 Pending 메시지 조회
        PendingMessages pending = executeWithConsumerGroupRetry(() ->
            redisTemplate.opsForStream().pending(
                KEY,
                CONSUMER_GROUP,
                Range.unbounded(),
                batchSize,
                minimumIdleTime
            )
        );
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }

        //회수할 Pending 메시지들의 ID 목록
        RecordId[] recordIds = pending.stream()
            .map(message -> message.getId())
            .toArray(RecordId[]::new);

        //회수해온 메시지 반환
        return redisTemplate.opsForStream().claim(
            KEY,
            CONSUMER_GROUP,
            consumerName,
            minimumIdleTime,
            recordIds
        );
    }

    // ACK와 메시지 삭제를 원자적으로 실행해 처리된 메시지가 Stream에 남지 않게 한다.
    public void acknowledgeAndDelete(MapRecord<String, Object, Object> record) {
        redisTemplate.execute(
            ACKNOWLEDGE_AND_DELETE_SCRIPT,
            List.of(KEY),
            CONSUMER_GROUP,
            record.getId().getValue()
        );
    }

    //메시지에서 groupBuyId 추출
    public Long getGroupBuyId(MapRecord<String, Object, Object> record) {
        Object value = record.getValue().get(FIELD_GROUP_BUY_ID);
        if (value == null) {
            throw new IllegalArgumentException("groupBuyId is missing");
        }
        return Long.valueOf(value.toString());
    }

    /*
        Consumer Group 생성을 시도하고 이미 존재하면 그대로 사용함으로써
        그룹이 준비된 상태임을 보장하는 메서드
    */
    private void ensureConsumerGroup() {
        //로컬 플래그 확인
        //이미 준비 됬으면 redis 요청 없이 종료
        if (consumerGroupReady.get()) {
            return;
        }

        //여러 스레드가 동시에 생성하지 않도록 동기화
        synchronized (consumerGroupReady) {
            if (consumerGroupReady.get()) {
                return;
            }

            try {
                //Stream이 먼저 생성되어 있어도 기존 메시지를 처음부터 소비한다.
                //Redis에 Consumer Group 생성을 시도합니다.
                //0-0부터 시작하므로 기존에 Stream에 쌓인 메시지도 처음부터 소비
                //그룹이 이미 있으면 Redis가 BUSYGROUP 오류를 반환
                redisTemplate.opsForStream().createGroup(
                    KEY,
                    ReadOffset.from("0-0"),
                    CONSUMER_GROUP
                );
            } catch (DataAccessException exception) {
                //BUSYGROUP 오류 반환은 정상 상황으로 간주, 아닌 경우만 예외처리
                if (!hasRedisError(exception, "BUSYGROUP")) {
                    throw exception;
                }
            }
            //로컬 플래그를 true로 변경
            consumerGroupReady.set(true);
        }
    }

    // Redis 초기화 등으로 Consumer Group이 사라졌으면 로컬 상태를 폐기하고 한 번 복구한다.
    private <T> T executeWithConsumerGroupRetry(Supplier<T> operation) {
        ensureConsumerGroup();
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            if (!hasRedisError(exception, "NOGROUP")) {
                throw exception;
            }

            consumerGroupReady.set(false);
            ensureConsumerGroup();
            return operation.get();
        }
    }

    private boolean hasRedisError(DataAccessException exception, String errorCode) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains(errorCode)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
