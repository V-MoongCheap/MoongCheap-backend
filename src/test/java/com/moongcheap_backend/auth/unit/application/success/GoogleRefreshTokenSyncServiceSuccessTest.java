package com.moongcheap_backend.auth.unit.application.success;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.GoogleRefreshTokenSyncService;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleRefreshTokenSyncServiceSuccessTest {

    @Mock SocialCredentialRepository socialCredentialRepository;
    @Mock EncryptionService encryptionService;

    @InjectMocks
    GoogleRefreshTokenSyncService service;

    @Nested
    @DisplayName("updateRefreshToken")
    class UpdateRefreshTokenTest {

        @Test
        void 존재하는_Google_SocialCredential에_암호화된_refresh_token이_저장된다() {
            SocialCredential cred = mock(SocialCredential.class);
            when(socialCredentialRepository.findByMemberIdAndProvider(1L, SocialProvider.GOOGLE))
                .thenReturn(Optional.of(cred));
            when(encryptionService.encrypt("plain-refresh-token")).thenReturn("enc-refresh-token");

            service.updateRefreshToken(1L, "plain-refresh-token");

            verify(cred).updateRefreshToken("enc-refresh-token");
        }

        @Test
        void SocialCredential이_없으면_아무_동작도_하지_않는다() {
            when(socialCredentialRepository.findByMemberIdAndProvider(1L, SocialProvider.GOOGLE))
                .thenReturn(Optional.empty());

            service.updateRefreshToken(1L, "plain-refresh-token");

            verify(encryptionService, never()).encrypt(org.mockito.ArgumentMatchers.anyString());
        }
    }
}
