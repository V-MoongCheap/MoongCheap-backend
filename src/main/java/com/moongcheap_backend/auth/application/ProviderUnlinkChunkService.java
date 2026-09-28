package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import com.moongcheap_backend.auth.infrastructure.PendingProviderUnlinkRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider unlink 워커의 3-phase (claim / markDone / markFailed) 를 각각 짧은 트랜잭션으로 분리.
 * 외부 HTTP 호출은 이 서비스 밖에서 처리하여 트랜잭션 안에서 I/O를 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProviderUnlinkChunkService {

    private final PendingProviderUnlinkRepository repository;

    /**
     * chunkSize 만큼 처리 대상 row 를 잠금 확보하고, next_attempt_at 을 미래로 밀어 큐에서 숨긴다.
     * 이 트랜잭션이 커밋되면 다른 pod / tick 은 hideUntil 이전에는 이 row 를 다시 잡을 수 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public List<PendingProviderUnlink> claim(int chunkSize, Duration hideDuration) {
        LocalDateTime now = LocalDateTime.now();
        List<PendingProviderUnlink> rows = repository.selectClaimable(now, chunkSize);
        LocalDateTime hideUntil = now.plus(hideDuration);
        rows.forEach(r -> r.hideUntil(hideUntil));
        return rows;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void markDone(Long id) {
        repository.deleteById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void markFailed(Long id, int maxRetry) {
        repository.findById(id).ifPresent(p -> {
            p.scheduleRetry(LocalDateTime.now());
            if (p.getRetryCount() > maxRetry) {
                p.markDeadLettered();
            }
        });
    }
}
