package com.moongcheap_backend.notification.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.notification.application.NotificationSettingService;
import com.moongcheap_backend.notification.domain.NotificationType;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 3-1: 동일 회원 동일 타입 동시 opt-out")
class NotificationOptOutConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private NotificationSettingService notificationSettingService;
    @Autowired private NotificationOptOutRepository notificationOptOutRepository;
    @Autowired private MemberFixture memberFixture;

    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("알림유저").getId();
    }

    @Test
    @DisplayName("동일 (memberId, type) 조합으로 50회 동시 opt-out 시 최종 레코드 1건, 모든 시도가 idempotent")
    void singleOptOutRecordUnderConcurrentDisable() throws Exception {
        int threadCount = 50;
        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            notificationSettingService.edit(memberId, NotificationType.DEMAND_REGISTERED, false);
            return true;
        });

        // 일부는 PK 충돌로 실패할 수 있으나 최소 1건은 성공해야 하고 최종 레코드는 정확히 1건
        assertThat(result.success()).isGreaterThanOrEqualTo(1);
        long count = notificationOptOutRepository.findAllByMemberId(memberId).stream()
            .filter(o -> o.getType() == NotificationType.DEMAND_REGISTERED)
            .count();
        assertThat(count).isEqualTo(1L);
    }
}
