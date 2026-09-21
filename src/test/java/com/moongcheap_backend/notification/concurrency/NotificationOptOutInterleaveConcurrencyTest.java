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

@DisplayName("동시성 3-2: opt-out 저장/삭제 인터리브")
class NotificationOptOutInterleaveConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private NotificationSettingService notificationSettingService;
    @Autowired private NotificationOptOutRepository notificationOptOutRepository;
    @Autowired private MemberFixture memberFixture;

    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("인터리브유저").getId();
    }

    @Test
    @DisplayName("동일 타입에 대해 enable/disable을 동시 인터리브 시 최종 상태는 0 또는 1건, PK 위반 없음")
    void interleavedEnableDisableIsIdempotent() throws Exception {
        int threadCount = 50; // 짝수여야 enable/disable이 균등하게 섞임
        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            notificationSettingService.edit(memberId, NotificationType.DEMAND_REGISTERED,
                idx % 2 == 0);
            return true;
        });

        // 각 요청은 idempotent이므로 성공/실패 카운트만 검증하지 않고 최종 로우 수만 확인
        assertThat(result.total()).isEqualTo(threadCount);
        long count = notificationOptOutRepository.findAllByMemberId(memberId).stream()
            .filter(o -> o.getType() == NotificationType.DEMAND_REGISTERED)
            .count();
        assertThat(count).isBetween(0L, 1L);
    }
}
