package com.moongcheap_backend.common.metrics;

import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** scrape에서는 캐시만 읽는다. 공유 DB/Redis 값은 인스턴스 간 합산하지 않는다. */
@Component
@ConditionalOnProperty(name = "moongcheap.metrics.backlog.enabled", havingValue = "true")
public class BacklogMetrics {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final MeterRegistry registry;
    private final Map<String, Double> values = new ConcurrentHashMap<>();
    private static final DefaultRedisScript<List> QUEUE = new DefaultRedisScript<>("""
        local t = redis.call('TIME')
        local now = tonumber(t[1])*1000 + math.floor(tonumber(t[2])/1000)
        return {redis.call('ZCARD', KEYS[1]), redis.call('ZCOUNT', KEYS[1], '-inf', now)}
        """, List.class);
    private static final DefaultRedisScript<List> STREAM = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 0 then return {-1, -1} end
        local groups = redis.call('XINFO', 'GROUPS', KEYS[1])
        for _,g in ipairs(groups) do
            local name = ''; local pending = -1; local lag = -1
            for i=1,#g,2 do
                if g[i]=='name' then name=g[i+1] end
                if g[i]=='pending' then pending=g[i+1] end
                if g[i]=='lag' and g[i+1] then lag=g[i+1] end
            end
            if name==ARGV[1] then return {pending, lag} end
        end
        return {-1, -1}
        """, List.class);

    public BacklogMetrics(JdbcTemplate jdbc, StringRedisTemplate redis, MeterRegistry registry) {
        this.jdbc = new JdbcTemplate(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        this.jdbc.setQueryTimeout(3);
        this.redis = redis;
        this.registry = registry;
    }

    @Scheduled(scheduler = "metricsTaskScheduler", fixedDelayString = "${moongcheap.metrics.backlog.interval-ms:15000}",
        initialDelayString = "${moongcheap.metrics.backlog.initial-delay-ms:10000}")
    public void refresh() {
        collect("outbox", () -> {
            Map<String, double[]> snapshot = new java.util.HashMap<>();
            for (OutboxEventType type : OutboxEventType.values()) snapshot.put(type.name(), new double[]{0, 0});
            jdbc.query("""
                SELECT event_type, count(*) AS amount,
                  greatest(0, extract(epoch FROM ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                    - min(pending_since)))) AS age
                FROM outbox_event WHERE status = 'PENDING' GROUP BY event_type
                """, rs -> { snapshot.put(rs.getString("event_type"),
                    new double[]{rs.getDouble("amount"), rs.getDouble("age")}); });
            snapshot.forEach((type, v) -> {
                set("moongcheap.outbox.pending", "type", type, v[0]);
                set("moongcheap.outbox.oldest.seconds", "type", type, v[1]);
            });
            return true;
        });
        collect("payments", () -> {
            // UNKNOWN에는 실제 처리 중과 재시도 대기가 모두 포함된다.
            Map<String, Double> snapshot = new java.util.HashMap<>();
            for (String state : List.of("pending", "processing", "retry_wait", "lease_expired")) snapshot.put(state, 0d);
            jdbc.query("""
                SELECT CASE WHEN processing_token IS NOT NULL AND processing_deadline > CURRENT_TIMESTAMP
                  THEN 'processing' WHEN processing_token IS NOT NULL THEN 'lease_expired'
                  WHEN payments_status = 'UNKNOWN' THEN 'retry_wait' ELSE 'pending' END AS state,
                  count(*) AS amount FROM payments WHERE payments_status IN ('PENDING', 'UNKNOWN') GROUP BY 1
                """, rs -> { snapshot.put(rs.getString("state"), rs.getDouble("amount")); });
            snapshot.forEach((state, v) -> set("moongcheap.payment.backlog", "state", state, v));
            return true;
        });
        collect("postgres", () -> {
            jdbc.query("""
                SELECT count(*) FILTER (WHERE state = 'active') AS active,
                  count(*) FILTER (WHERE wait_event_type = 'Lock') AS waiting,
                  coalesce(max(extract(epoch FROM (CURRENT_TIMESTAMP - xact_start))), 0) AS oldest
                FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()
                """, rs -> {
                    set("moongcheap.postgres.connections", "state", "active", rs.getDouble("active"));
                    set("moongcheap.postgres.connections", "state", "lock_wait", rs.getDouble("waiting"));
                    set("moongcheap.postgres.oldest.transaction.seconds", "scope", "database", rs.getDouble("oldest"));
                });
            return true;
        });
        queue("judgment", "moongcheap:group-buy:judgment:pending");
        queue("payment", "moongcheap:payment:execution:scheduled");
        collect("order_stream", () -> {
            List<?> result = redis.execute(STREAM, List.of("moongcheap:group-buy:order-creation"), "group-buy-order-creators");
            if (result == null) throw new IllegalStateException("No stream snapshot");
            double pending = ((Number) result.get(0)).doubleValue();
            double lag = ((Number) result.get(1)).doubleValue();
            set("moongcheap.stream.messages", "state", "pending", pending < 0 ? Double.NaN : pending);
            set("moongcheap.stream.messages", "state", "lag", lag < 0 ? Double.NaN : lag);
            return pending >= 0 && lag >= 0;
        });
    }

    private void queue(String name, String key) {
        collect(name + "_queue", () -> {
            List<?> result = redis.execute(QUEUE, List.of(key));
            if (result == null) throw new IllegalStateException("No queue snapshot");
            set("moongcheap.queue.size", "queue", name, ((Number) result.get(0)).doubleValue());
            set("moongcheap.queue.due", "queue", name, ((Number) result.get(1)).doubleValue());
            return true;
        });
    }

    private void collect(String source, Supplier<Boolean> action) {
        try {
            boolean success = action.get();
            set("moongcheap.collector.up", "source", source, success ? 1 : 0);
            if (success) set("moongcheap.collector.last.success.timestamp.seconds", "source", source,
                System.currentTimeMillis() / 1000d);
        } catch (RuntimeException error) {
            set("moongcheap.collector.up", "source", source, 0);
            registry.counter("moongcheap.collector.errors", "source", source).increment();
        }
    }

    private void set(String name, String tag, String value, double number) {
        String key = name + ":" + value;
        values.put(key, number);
        registry.gauge(name, List.of(io.micrometer.core.instrument.Tag.of(tag, value)),
            values, map -> map.getOrDefault(key, Double.NaN));
    }
}
