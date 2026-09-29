package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import com.moongcheap_backend.auth.infrastructure.oauth.GoogleOAuth2Client;
import com.moongcheap_backend.auth.infrastructure.oauth.KakaoOAuth2Client;
import com.moongcheap_backend.common.crypto.EncryptionService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * pending_provider_unlink 큐를 소비해 provider 별 unlink API 를 호출한다.
 * Claim → external call → reconcile 3-phase 로 분리해 트랜잭션 안에서 외부 I/O 를 잡지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderUnlinkWorker {

    private final ProviderUnlinkChunkService chunkService;
    private final KakaoOAuth2Client kakaoOAuth2Client;
    private final GoogleOAuth2Client googleOAuth2Client;
    private final EncryptionService encryptionService;

    @Value("${moongcheap.auth.unlink-worker.chunk-size:20}")
    private int chunkSize;

    @Value("${moongcheap.auth.unlink-worker.max-retry:8}")
    private int maxRetry;

    @Value("${moongcheap.auth.unlink-worker.hide-duration-ms:300000}")
    private long hideDurationMs;

    @Scheduled(fixedDelayString = "${moongcheap.auth.unlink-worker.delay-ms:10000}")
    public void tick() {
        List<PendingProviderUnlink> claimed;
        try {
            claimed = chunkService.claim(chunkSize, Duration.ofMillis(hideDurationMs));
        } catch (Exception e) {
            log.error("provider unlink claim failed", e);
            return;
        }
        if (claimed.isEmpty()) {
            return;
        }
        for (PendingProviderUnlink p : claimed) {
            process(p);
        }
    }

    private void process(PendingProviderUnlink p) {
        try {
            callProvider(p);
            chunkService.markDone(p.getId());
        } catch (Exception e) {
            log.warn("provider unlink failed id={} provider={} providerId={} retryCount={}",
                p.getId(), p.getProvider(), p.getProviderId(), p.getRetryCount(), e);
            try {
                chunkService.markFailed(p.getId(), maxRetry);
            } catch (Exception reconcileFailure) {
                log.error("failed to reconcile unlink failure id={}", p.getId(), reconcileFailure);
            }
        }
    }

    private void callProvider(PendingProviderUnlink p) {
        switch (p.getProvider()) {
            case KAKAO -> kakaoOAuth2Client.unlink(p.getProviderId());
            case GOOGLE -> {
                String token = encryptionService.decrypt(p.getRevocationTokenEnc());
                googleOAuth2Client.revoke(token);
            }
        }
    }
}
