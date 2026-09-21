package com.moongcheap_backend.demand.unit.demand.application.success;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.demand.application.demand.DemandExpireChunkService;
import com.moongcheap_backend.demand.application.demand.DemandExpireScheduler;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemandExpireSchedulerSuccessTest {

    private static final int CHUNK_SIZE = 1000;

    @Mock
    private DemandExpireChunkService chunkService;

    @InjectMocks
    private DemandExpireScheduler scheduler;

    @Nested
    @DisplayName("expireOverdueUnassigned - 성공")
    class ExpireOverdueUnassignedTest {

        @Test
        void 한_번의_chunk_실행으로_완료된다() {
            when(chunkService.expireChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.of(CHUNK_SIZE - 1));

            scheduler.expireOverdueUnassigned();

            verify(chunkService, times(1)).expireChunk(any(), eq(CHUNK_SIZE));
        }

        @Test
        void 여러_chunk에_걸쳐_처리한다() {
            when(chunkService.expireChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.of(CHUNK_SIZE))
                .thenReturn(Optional.of(CHUNK_SIZE - 1));

            scheduler.expireOverdueUnassigned();

            verify(chunkService, times(2)).expireChunk(any(), eq(CHUNK_SIZE));
        }

        @Test
        void lock_획득_실패로_중단된다() {
            when(chunkService.expireChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.empty());

            scheduler.expireOverdueUnassigned();

            verify(chunkService, times(1)).expireChunk(any(), eq(CHUNK_SIZE));
        }

        @Test
        void MAX_CHUNKS_100까지_도달하면_중단된다() {
            when(chunkService.expireChunk(any(), eq(CHUNK_SIZE)))
                .thenReturn(Optional.of(CHUNK_SIZE));

            scheduler.expireOverdueUnassigned();

            verify(chunkService, times(100)).expireChunk(any(), eq(CHUNK_SIZE));
        }
    }
}
