package com.moongcheap_backend.demand.unit.demandBoard.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.demand.application.demandBoard.DemandBoardCancelChunkService;
import com.moongcheap_backend.demand.application.demandBoard.DemandBoardCancelScheduler;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemandBoardCancelSchedulerSuccessTest {

    private static final int CHUNK_SIZE = 1000;

    @Mock
    private DemandBoardCancelChunkService chunkService;

    @InjectMocks
    private DemandBoardCancelScheduler scheduler;

    @Nested
    @DisplayName("cancelOverdueGatheringBoards - 성공")
    class CancelOverdueGatheringBoardsTest {

        @Test
        void 한_번의_chunk_실행으로_완료된다() {
            when(chunkService.cancelChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.of(CHUNK_SIZE - 1));

            scheduler.cancelOverdueGatheringBoards();

            verify(chunkService, times(1)).cancelChunk(any(), eq(CHUNK_SIZE));
        }

        @Test
        void 배치_처리시각은_KST_현재시각을_사용한다() {
            ZoneId zoneSeoul = ZoneId.of("Asia/Seoul");
            LocalDateTime before = LocalDateTime.now(zoneSeoul);
            when(chunkService.cancelChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.of(0));

            scheduler.cancelOverdueGatheringBoards();

            ArgumentCaptor<LocalDateTime> thresholdCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
            verify(chunkService).cancelChunk(thresholdCaptor.capture(), eq(CHUNK_SIZE));
            assertThat(thresholdCaptor.getValue()).isBetween(
                before, LocalDateTime.now(zoneSeoul));
        }

        @Test
        void lock_획득_실패로_중단된다() {
            when(chunkService.cancelChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.empty());

            scheduler.cancelOverdueGatheringBoards();

            verify(chunkService, times(1)).cancelChunk(any(), eq(CHUNK_SIZE));
        }

        @Test
        void MAX_CHUNKS_100까지_도달하면_중단된다() {
            when(chunkService.cancelChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.of(CHUNK_SIZE));

            scheduler.cancelOverdueGatheringBoards();

            verify(chunkService, times(100)).cancelChunk(any(), eq(CHUNK_SIZE));
        }
    }
}
