package com.moongcheap_backend.auth.domain;

import com.moongcheap_backend.common.entity.BaseTimeEntity;
import com.moongcheap_backend.member.domain.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "pending_provider_unlink")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingProviderUnlink extends BaseTimeEntity {

    private static final int MAX_RETRY_DELAY_SECONDS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    // Google: 암호화된 refresh_token. Kakao: null (admin key 모드로 provider_id 만 사용).
    @Column(name = "revocation_token_enc", columnDefinition = "TEXT")
    private String revocationTokenEnc;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "dead_lettered", nullable = false)
    private boolean deadLettered;

    private PendingProviderUnlink(Long memberId, SocialProvider provider, String providerId,
        String revocationTokenEnc, LocalDateTime now) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerId = providerId;
        this.revocationTokenEnc = revocationTokenEnc;
        this.retryCount = 0;
        this.nextAttemptAt = now;
        this.deadLettered = false;
    }

    public static PendingProviderUnlink of(Long memberId, SocialProvider provider,
        String providerId, String revocationTokenEnc, LocalDateTime now) {
        return new PendingProviderUnlink(memberId, provider, providerId, revocationTokenEnc, now);
    }

    public void scheduleRetry(LocalDateTime now) {
        this.retryCount++;
        long delaySeconds = Math.min(1L << Math.min(retryCount, 8), MAX_RETRY_DELAY_SECONDS);
        this.nextAttemptAt = now.plusSeconds(delaySeconds);
    }

    public void markDeadLettered() {
        this.deadLettered = true;
    }

    /**
     * claim 직후 호출. 외부 API 호출이 진행되는 동안 다른 pod 가 같은 row 를 다시
     * 잡지 못하도록 next_attempt_at 을 미래로 이동시켜 큐에서 숨긴다.
     */
    public void hideUntil(LocalDateTime until) {
        this.nextAttemptAt = until;
    }
}
