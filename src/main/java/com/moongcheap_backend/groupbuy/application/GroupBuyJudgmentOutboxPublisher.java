package com.moongcheap_backend.groupbuy.application;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupBuyJudgmentOutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final GroupBuyJudgmentOutboxPublishService publishService;

    // 짧은 주기로 PENDING Outbox를 Redis Sorted Set에 등록한다.
    @Scheduled(fixedDelayString = "${moongcheap.group-buy.outbox-publish-delay-ms:1000}")
    public void publishPending() {
        publishService.publishBatch(LocalDateTime.now(ZONE_SEOUL), BATCH_SIZE);
    }
}
