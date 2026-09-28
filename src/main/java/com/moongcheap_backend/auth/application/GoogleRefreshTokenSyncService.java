package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google refresh_token 을 SocialCredential 에 반영한다.
 * OAuth2LoginSuccessHandler 는 프록시가 아니므로 self-invocation 으로 @Transactional 이
 * 무시된다. 프록시 경유를 위해 별도 서비스 빈으로 분리하여 트랜잭션을 보장한다.
 */
@Service
@RequiredArgsConstructor
public class GoogleRefreshTokenSyncService {

    private final SocialCredentialRepository socialCredentialRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public void updateRefreshToken(Long memberId, String plainRefreshToken) {
        socialCredentialRepository
            .findByMemberIdAndProvider(memberId, SocialProvider.GOOGLE)
            .ifPresent(cred -> cred.updateRefreshToken(
                encryptionService.encrypt(plainRefreshToken)));
    }
}
