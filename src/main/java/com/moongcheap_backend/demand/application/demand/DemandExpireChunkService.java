package com.moongcheap_backend.demand.application.demand;

import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.common.lock.AdvisoryLockKeys;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemandExpireChunkService {

    private final AdvisoryLockAdaptor advisoryLockAdaptor;
    private final DemandRepository demandRepository;

    @Retry(name = "chunkRetry")
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public Optional<Integer> expireChunk(LocalDateTime threshold, int chunkSize) {
        if (!advisoryLockAdaptor.tryAcquireXactLock(AdvisoryLockKeys.DEMAND_EXPIRE_BATCH)) {
            return Optional.empty();
        }
        return Optional.of(demandRepository.expireChunk(threshold, chunkSize));
    }
}
