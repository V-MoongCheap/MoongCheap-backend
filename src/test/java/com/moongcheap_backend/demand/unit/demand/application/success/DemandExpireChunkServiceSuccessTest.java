package com.moongcheap_backend.demand.unit.demand.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.common.lock.AdvisoryLockKeys;
import com.moongcheap_backend.demand.application.demand.DemandExpireChunkService;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemandExpireChunkServiceSuccessTest {

    @Mock
    private AdvisoryLockAdaptor advisoryLockAdaptor;

    @Mock
    private DemandRepository demandRepository;

    @InjectMocks
    private DemandExpireChunkService service;

    @Nested
    @DisplayName("expireChunk - 성공")
    class ExpireChunkTest {

        @Test
        void lock을_획득하고_만료된_수요를_일괄_처리한다() {
            LocalDateTime threshold = LocalDateTime.now();
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_EXPIRE_BATCH))
                .thenReturn(true);
            when(demandRepository.expireChunk(threshold, 1000)).thenReturn(5);

            Optional<Integer> result = service.expireChunk(threshold, 1000);

            assertThat(result).contains(5);
            verify(demandRepository).expireChunk(threshold, 1000);
        }

        @Test
        void 다른_프로세스가_lock을_보유중이면_empty를_반환한다() {
            LocalDateTime threshold = LocalDateTime.now();
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_EXPIRE_BATCH))
                .thenReturn(false);

            Optional<Integer> result = service.expireChunk(threshold, 1000);

            assertThat(result).isEmpty();
            verify(demandRepository, never()).expireChunk(any(), anyInt());
        }

        @Test
        void 처리할_만료_수요가_없으면_0을_반환한다() {
            LocalDateTime threshold = LocalDateTime.now();
            when(advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_EXPIRE_BATCH))
                .thenReturn(true);
            when(demandRepository.expireChunk(threshold, 1000)).thenReturn(0);

            Optional<Integer> result = service.expireChunk(threshold, 1000);

            assertThat(result).contains(0);
        }
    }
}
