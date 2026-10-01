package com.moongcheap_backend.auth.unit.application.success;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.SocialLinkService;
import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialLinkServiceSuccessTest {

    @Mock private SocialCredentialRepository socialCredentialRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private AdvisoryLockAdaptor advisoryLockAdaptor;

    @InjectMocks
    private SocialLinkService socialLinkService;

    @Nested
    @DisplayName("link - 성공")
    class LinkTest {

        @Test
        void 계정_연결이_되어있지_않은_social_계정으로_계정_연결() {
            Long memberId = 1L;
            when(socialCredentialRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao123"))
                .thenReturn(Optional.empty());
            when(socialCredentialRepository.findByMemberIdAndProvider(memberId, SocialProvider.KAKAO))
                .thenReturn(Optional.empty());

            socialLinkService.link(memberId, SocialProvider.KAKAO, "kakao123");

            verify(socialCredentialRepository).save(any(SocialCredential.class));
        }

        @Test
        void 이미_연결된_자신의_social_계정으로_재연결을_시도한다() {
            Long memberId = 1L;
            SocialCredential existing = mock(SocialCredential.class);
            when(existing.getMemberId()).thenReturn(memberId);
            when(socialCredentialRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao123"))
                .thenReturn(Optional.of(existing));

            socialLinkService.link(memberId, SocialProvider.KAKAO, "kakao123");

            verify(socialCredentialRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unlink - 성공")
    class UnlinkTest {

        @Test
        void 연결된_자신의_SOCIAL_계정을_삭제한다_다른_소셜_계정이_존재하는_경우() {
            Long memberId = 1L;
            SocialCredential target = mock(SocialCredential.class);
            when(socialCredentialRepository.findByMemberIdAndProvider(memberId, SocialProvider.KAKAO))
                .thenReturn(Optional.of(target));
            when(socialCredentialRepository.countByMemberId(memberId)).thenReturn(2L);

            socialLinkService.unlink(memberId, SocialProvider.KAKAO);

            verify(socialCredentialRepository).delete(target);
        }

        @Test
        void 연결된_자신의_SOCIAL_계정을_삭제한다_로컬_계정이_존재하는_경우() {
            Long memberId = 1L;
            SocialCredential target = mock(SocialCredential.class);
            when(socialCredentialRepository.findByMemberIdAndProvider(memberId, SocialProvider.KAKAO))
                .thenReturn(Optional.of(target));
            when(socialCredentialRepository.countByMemberId(memberId)).thenReturn(1L);
            when(localCredentialRepository.existsByMemberId(memberId)).thenReturn(true);

            socialLinkService.unlink(memberId, SocialProvider.KAKAO);

            verify(socialCredentialRepository).delete(target);
            verify(localCredentialRepository, never()).deleteByMemberId(any());
        }
    }
}
