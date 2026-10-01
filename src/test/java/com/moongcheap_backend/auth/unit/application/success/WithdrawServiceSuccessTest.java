package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.WithdrawService;
import com.moongcheap_backend.auth.application.event.MemberWithdrawnEvent;
import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import com.moongcheap_backend.auth.infrastructure.PendingProviderUnlinkRepository;
import com.moongcheap_backend.auth.infrastructure.port.WithdrawEligibilityChecker;
import com.moongcheap_backend.auth.presentation.dto.WithdrawRequestDto;
import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private SocialCredentialRepository socialCredentialRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private NotificationOptOutRepository notificationOptOutRepository;
    @Mock private PendingProviderUnlinkRepository pendingProviderUnlinkRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private WithdrawEligibilityChecker eligibilityChecker;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WithdrawService withdrawService;

    @Nested
    @DisplayName("withdraw - 성공 (로컬 계정)")
    class LocalWithdrawTest {

        @Test
        void 로컬_계정을_가진_회원이_올바른_비밀번호로_탈퇴한다() {
            Long memberId = 1L;
            String rawPassword = "pass1234!";
            String encodedPassword = "encoded";
            WithdrawRequestDto dto = new WithdrawRequestDto(rawPassword);

            Member member = mock(Member.class);
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn(encodedPassword);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId))
                .thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of());

            withdrawService.withdraw(memberId, dto);

            verify(pendingProviderUnlinkRepository, never()).save(any());
            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(localCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
            verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(memberId));
        }
    }

    @Nested
    @DisplayName("withdraw - 성공 (소셜 전용)")
    class SocialOnlyWithdrawTest {

        @Test
        void kakao_소셜_전용_계정_회원이_탈퇴하면_outbox에_저장된다() {
            Long memberId = 1L;

            Member member = mock(Member.class);
            SocialCredential kakaoCredential = mock(SocialCredential.class);
            when(kakaoCredential.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(kakaoCredential.getProviderId()).thenReturn("kakao123");
            when(kakaoCredential.getRefreshTokenEnc()).thenReturn(null);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
            when(socialCredentialRepository.findAllByMemberId(memberId))
                .thenReturn(List.of(kakaoCredential));

            withdrawService.withdraw(memberId, null);

            ArgumentCaptor<PendingProviderUnlink> captor =
                ArgumentCaptor.forClass(PendingProviderUnlink.class);
            verify(pendingProviderUnlinkRepository, times(1)).save(captor.capture());
            PendingProviderUnlink saved = captor.getValue();
            assertThat(saved.getMemberId()).isEqualTo(memberId);
            assertThat(saved.getProvider()).isEqualTo(SocialProvider.KAKAO);
            assertThat(saved.getProviderId()).isEqualTo("kakao123");
            assertThat(saved.getRevocationTokenEnc()).isNull();

            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
            verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(memberId));
        }

        @Test
        void google_소셜_전용_계정_회원이_탈퇴하면_refresh_token이_outbox에_담긴다() {
            Long memberId = 1L;

            Member member = mock(Member.class);
            SocialCredential googleCredential = mock(SocialCredential.class);
            when(googleCredential.getProvider()).thenReturn(SocialProvider.GOOGLE);
            when(googleCredential.getProviderId()).thenReturn("google-sub-42");
            when(googleCredential.getRefreshTokenEnc()).thenReturn("enc-refresh-token");

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
            when(socialCredentialRepository.findAllByMemberId(memberId))
                .thenReturn(List.of(googleCredential));

            withdrawService.withdraw(memberId, null);

            ArgumentCaptor<PendingProviderUnlink> captor =
                ArgumentCaptor.forClass(PendingProviderUnlink.class);
            verify(pendingProviderUnlinkRepository, times(1)).save(captor.capture());
            PendingProviderUnlink saved = captor.getValue();
            assertThat(saved.getMemberId()).isEqualTo(memberId);
            assertThat(saved.getProvider()).isEqualTo(SocialProvider.GOOGLE);
            assertThat(saved.getProviderId()).isEqualTo("google-sub-42");
            assertThat(saved.getRevocationTokenEnc()).isEqualTo("enc-refresh-token");

            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
            verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(memberId));
        }

        @Test
        void 소셜_로컬_계정을_모두_가진_회원이_탈퇴하면_각_provider가_outbox에_저장된다() {
            Long memberId = 1L;
            String rawPassword = "pass1234!";
            String encodedPassword = "encoded";
            WithdrawRequestDto dto = new WithdrawRequestDto(rawPassword);

            Member member = mock(Member.class);
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn(encodedPassword);

            SocialCredential kakaoCredential = mock(SocialCredential.class);
            when(kakaoCredential.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(kakaoCredential.getProviderId()).thenReturn("kakao123");
            when(kakaoCredential.getRefreshTokenEnc()).thenReturn(null);

            SocialCredential googleCredential = mock(SocialCredential.class);
            when(googleCredential.getProvider()).thenReturn(SocialProvider.GOOGLE);
            when(googleCredential.getProviderId()).thenReturn("google-sub-42");
            when(googleCredential.getRefreshTokenEnc()).thenReturn("enc-refresh-token");

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId))
                .thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
            when(socialCredentialRepository.findAllByMemberId(memberId))
                .thenReturn(List.of(kakaoCredential, googleCredential));

            withdrawService.withdraw(memberId, dto);

            verify(pendingProviderUnlinkRepository, times(2)).save(any(PendingProviderUnlink.class));
            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(localCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
            verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(memberId));
        }
    }
}
