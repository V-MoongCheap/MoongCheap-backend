package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.ProviderUnlinkChunkService;
import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import com.moongcheap_backend.auth.infrastructure.PendingProviderUnlinkRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
class ProviderUnlinkChunkServiceSuccessTest {

    @Mock private PendingProviderUnlinkRepository repository;

    @InjectMocks
    private ProviderUnlinkChunkService service;

    @Nested
    @DisplayName("claim")
    class ClaimTest {

        @Test
        void claim은_selectClaimable_결과의_next_attempt_at을_미래로_민다() {
            PendingProviderUnlink row = mock(PendingProviderUnlink.class);
            when(repository.selectClaimable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row));

            List<PendingProviderUnlink> result = service.claim(5, Duration.ofMinutes(5));

            assertThat(result).containsExactly(row);
            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(row).hideUntil(captor.capture());
            assertThat(captor.getValue()).isAfter(LocalDateTime.now().plusMinutes(4));
        }

        @Test
        void claim_결과가_비면_빈_리스트를_반환한다() {
            when(repository.selectClaimable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of());

            List<PendingProviderUnlink> result = service.claim(5, Duration.ofMinutes(5));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("markDone")
    class MarkDoneTest {

        @Test
        void markDone은_row를_삭제한다() {
            service.markDone(42L);
            verify(repository).deleteById(42L);
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailedTest {

        @Test
        void 재시도_횟수가_임계치_이하면_scheduleRetry만_호출된다() {
            PendingProviderUnlink row = mock(PendingProviderUnlink.class);
            when(row.getRetryCount()).thenReturn(3);
            when(repository.findById(42L)).thenReturn(Optional.of(row));

            service.markFailed(42L, 8);

            verify(row).scheduleRetry(any(LocalDateTime.class));
            verify(row, org.mockito.Mockito.never()).markDeadLettered();
        }

        @Test
        void 재시도_횟수가_임계치를_초과하면_markDeadLettered가_호출된다() {
            PendingProviderUnlink row = mock(PendingProviderUnlink.class);
            when(row.getRetryCount()).thenReturn(9);  // scheduleRetry 후 9
            when(repository.findById(42L)).thenReturn(Optional.of(row));

            service.markFailed(42L, 8);

            verify(row).scheduleRetry(any(LocalDateTime.class));
            verify(row).markDeadLettered();
        }

        @Test
        void row가_이미_삭제된_경우_예외없이_통과한다() {
            when(repository.findById(999L)).thenReturn(Optional.empty());
            service.markFailed(999L, 8);
        }
    }
}
