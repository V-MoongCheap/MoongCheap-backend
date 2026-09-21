package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.common.lock.AdvisoryLockKeys;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLinkService {

    private static final String CREDENTIAL_LOCK_TIMEOUT = "3s";

    private final SocialCredentialRepository socialCredentialRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final AdvisoryLockAdaptor advisoryLockAdaptor;

    @Transactional
    public void link(Long memberId, SocialProvider provider, String providerId) {
        Optional<SocialCredential> existing = socialCredentialRepository.findByProviderAndProviderId(
            provider, providerId);
        if (existing.isPresent()) {
            if (!existing.get().getMemberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
            }
            return;
        }
        if (socialCredentialRepository.findByMemberIdAndProvider(memberId, provider).isPresent()) {
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
        }
        socialCredentialRepository.save(SocialCredential.builder()
            .memberId(memberId)
            .provider(provider)
            .providerId(providerId)
            .build());
    }

    // 마지막 로그인 수단 검사가 count 기반이라 phantom read 방지를 위해 Advisory Lock 사용
    @Transactional
    public void unlink(Long memberId, SocialProvider provider) {
        advisoryLockAdaptor.acquireXactLock(AdvisoryLockKeys.credentialWrite(memberId),
            CREDENTIAL_LOCK_TIMEOUT);
        SocialCredential target = socialCredentialRepository.findByMemberIdAndProvider(memberId,
                provider)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        long socialCount = socialCredentialRepository.countByMemberId(memberId);
        boolean hasLocal = localCredentialRepository.existsByMemberId(memberId);
        int remainingCredentials = (int) socialCount - 1 + (hasLocal ? 1 : 0);
        if (remainingCredentials < 1) {
            throw new BusinessException(ErrorCode.LAST_CREDENTIAL_CANNOT_UNLINK);
        }
        socialCredentialRepository.delete(target);
    }
}
