package com.moongcheap_backend.groupbuy.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 테스트 대상: {@link GroupBuyJudgmentOutboxPublisher}의 Outbox 배치 실행 기능
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("공동구매 판정 Outbox 스케줄러 - 해피 케이스")
class GroupBuyJudgmentOutboxPublisherUnitTest {

    @Mock
    private GroupBuyJudgmentOutboxPublishService publishService;

    @InjectMocks
    private GroupBuyJudgmentOutboxPublisher publisher;

    @Test
    void 발행_가능한_Outbox를_100개씩_조회해_발행한다() {
        publisher.publishPending();

        verify(publishService).publishBatch(any(LocalDateTime.class), eq(100));
    }
}
