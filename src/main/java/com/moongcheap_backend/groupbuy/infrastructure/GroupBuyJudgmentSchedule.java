package com.moongcheap_backend.groupbuy.infrastructure;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupBuyJudgmentSchedule {

    // 하나의 Sorted Set에서 member는 GroupBuy ID, score는 판정 예정 시각으로 사용한다.
    private static final String KEY = "moongcheap:group-buy:judgment:pending";
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;

    // 같은 GroupBuy ID를 다시 등록해도 member가 중복되지 않고 score만 갱신된다.
    public void schedule(Long groupBuyId, LocalDateTime scheduledAt) {
        redisTemplate.opsForZSet().add(
            KEY,
            groupBuyId.toString(),
            toEpochMillis(scheduledAt)
        );
    }

    // 현재 시각까지 도래한 판정 대상을 오래된 순서대로 제한된 개수만 조회한다.
    public Set<String> findDue(LocalDateTime now, int batchSize) {
        Set<String> due = redisTemplate.opsForZSet()
            .rangeByScore(KEY, 0, toEpochMillis(now), 0, batchSize);
        return due == null ? Set.of() : due;
    }

    // DB 판정이 커밋된 대상만 대기열에서 제거한다.
    public void remove(Long groupBuyId) {
        redisTemplate.opsForZSet().remove(KEY, groupBuyId.toString());
    }

    public void remove(String member) {
        redisTemplate.opsForZSet().remove(KEY, member);
    }

    private double toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE_SEOUL).toInstant().toEpochMilli();
    }
}
