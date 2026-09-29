package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.application.PaymentQueueProperties;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSchedule {
    public static final String KEY = "moongcheap:payment:execution:scheduled";
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>("""
        local t = redis.call('TIME')
        local now = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)
        local item = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'LIMIT', 0, 1)
        if #item == 0 then return nil end
        redis.call('ZADD', KEYS[1], now + tonumber(ARGV[1]), item[1])
        return item[1]
        """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final PaymentQueueProperties properties;

    public void schedule(Long paymentId, LocalDateTime scheduledAt) {
        redisTemplate.opsForZSet().add(KEY, paymentId.toString(),
            scheduledAt.atZone(ZONE_SEOUL).toInstant().toEpochMilli());
    }

    public Optional<Long> claimDue() {
        String member = redisTemplate.execute(CLAIM_SCRIPT, List.of(KEY),
            Long.toString(properties.getVisibilityDelay().toMillis()));
        if (member == null) return Optional.empty();
        try {
            return Optional.of(Long.valueOf(member));
        } catch (NumberFormatException exception) {
            redisTemplate.opsForZSet().remove(KEY, member);
            return Optional.empty();
        }
    }

    public void remove(Long paymentId) {
        redisTemplate.opsForZSet().remove(KEY, paymentId.toString());
    }

    public Double score(Long paymentId) {
        return redisTemplate.opsForZSet().score(KEY, paymentId.toString());
    }
}
