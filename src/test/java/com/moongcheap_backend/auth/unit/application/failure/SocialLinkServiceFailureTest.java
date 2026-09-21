package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.SocialLinkService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
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
class SocialLinkServiceFailureTest {

    @Mock private SocialCredentialRepository socialCredentialRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private AdvisoryLockAdaptor advisoryLockAdaptor;

    @InjectMocks
    private SocialLinkService socialLinkService;

    @Nested
    @DisplayName("link - 실패")
    class LinkFailureTest {

        @Test
        void 이미_다른_계정과_연결된_SOCIAL_계정으로_연결을_시도한다() {
            Long memberId = 1L;
            Long otherMemberId = 2L;
            SocialCredential existing = mock(SocialCredential.class);
            when(existing.getMemberId()).thenReturn(otherMemberId);
            when(socialCredentialRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao123"))
                .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> socialLinkService.link(memberId, SocialProvider.KAKAO, "kakao123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ALREADY_LINKED);

            verify(socialCredentialRepository, never()).save(any());
        }

        @Test
        void 이미_연결된_제공자의_다른_SOCIAL_계정으로_연결한다() {
            Long memberId = 1L;
            when(socialCredentialRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "newKakao"))
                .thenReturn(Optional.empty());
            when(socialCredentialRepository.findByMemberIdAndProvider(memberId, SocialProvider.KAKAO))
                .thenReturn(Optional.of(mock(SocialCredential.class)));

            assertThatThrownBy(() -> socialLinkService.link(memberId, SocialProvider.KAKAO, "newKakao"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ALREADY_LINKED);

            verify(socialCredentialRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unlink - 실패")
    class UnlinkFailureTest {

        @Test
        void 연동되지_않은_소셜_계정을_해제한다() {
            Long memberId = 1L;
            when(socialCredentialRepository.findByMemberIdAndProvider(memberId, SocialProvider.KAKAO))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> socialLinkService.unlink(memberId, SocialProvider.KAKAO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        void 연결된_SOCIAL_또는_LOCAL_정보가_1개밖에_없으면_계정_삭제가_불가능하다() {
            Long memberId = 1L;
            SocialCredential target = mock(SocialCredential.class);
            when(socialCredentialRepository.findByMemberIdAndProvider(memberId, SocialProvider.KAKAO))
                .thenReturn(Optional.of(target));
            when(socialCredentialRepository.countByMemberId(memberId)).thenReturn(1L);
            when(localCredentialRepository.existsByMemberId(memberId)).thenReturn(false);

            assertThatThrownBy(() -> socialLinkService.unlink(memberId, SocialProvider.KAKAO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LAST_CREDENTIAL_CANNOT_UNLINK);
        }
    }
}
