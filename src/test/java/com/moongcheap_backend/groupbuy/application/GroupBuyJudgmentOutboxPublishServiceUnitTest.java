package com.moongcheap_backend.groupbuy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.domain.OutboxEventStatus;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 테스트 대상: {@link GroupBuyJudgmentOutboxPublishService}의 Redis 발행 및 재시도 기능
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyJudgmentOutboxPublishServiceUnitTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private GroupBuyJudgmentSchedule judgmentSchedule;

    @InjectMocks
    private GroupBuyJudgmentOutboxPublishService publishService;

    @Test
    @DisplayName("해피 케이스 - Redis 등록 후 Outbox 발행 완료")
    void Redis에_등록되면_Outbox를_발행_완료로_변경한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime scheduledAt = now.plusHours(1);
        OutboxEvent event = OutboxEvent.groupBuyJudgmentScheduled(1L, scheduledAt, now);
        when(outboxEventRepository.findPublishableForUpdate(now, 100))
            .thenReturn(List.of(event));

        int published = publishService.publishBatch(now, 100);

        assertThat(published).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(now);
        verify(judgmentSchedule).schedule(1L, scheduledAt);
    }

    @Test
    @DisplayName("예외 케이스 - Redis 등록 실패 후 Outbox 재시도 예약")
    void Redis_등록에_실패하면_Outbox를_재시도_상태로_유지한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime scheduledAt = now.plusHours(1);
        OutboxEvent event = OutboxEvent.groupBuyJudgmentScheduled(1L, scheduledAt, now);
        when(outboxEventRepository.findPublishableForUpdate(now, 100))
            .thenReturn(List.of(event));
        doThrow(new IllegalStateException("Redis unavailable"))
            .when(judgmentSchedule).schedule(1L, scheduledAt);

        publishService.publishBatch(now, 100);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(now);
    }

    @Test
    @DisplayName("해피 케이스 - 발행할 Outbox가 없는 빈 배치")
    void 발행할_Outbox가_없으면_Redis를_호출하지_않는다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 10, 0);
        when(outboxEventRepository.findPublishableForUpdate(now, 100))
            .thenReturn(List.of());

        int published = publishService.publishBatch(now, 100);

        assertThat(published).isZero();
        verifyNoInteractions(judgmentSchedule);
    }
}
