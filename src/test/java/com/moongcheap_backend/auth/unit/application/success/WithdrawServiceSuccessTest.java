package com.moongcheap_backend.auth.unit.application.success;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.WithdrawService;
import com.moongcheap_backend.auth.infrastructure.oauth.GoogleOAuth2Client;
import com.moongcheap_backend.auth.infrastructure.oauth.KakaoOAuth2Client;
import com.moongcheap_backend.auth.infrastructure.port.WithdrawEligibilityChecker;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private SocialCredentialRepository socialCredentialRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private NotificationOptOutRepository notificationOptOutRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionManager sessionManager;
    @Mock private WithdrawEligibilityChecker eligibilityChecker;
    @Mock private KakaoOAuth2Client kakaoOAuth2Client;
    @Mock private GoogleOAuth2Client googleOAuth2Client;

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

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of());

            setupRequestContext(null);
            try {
                withdrawService.withdraw(memberId, dto);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }

            verify(sessionManager).invalidateAllForMember(memberId);
            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(localCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
        }
    }

    @Nested
    @DisplayName("withdraw - 성공 (소셜 전용)")
    class SocialOnlyWithdrawTest {

        @Test
        void kakao_소셜_전용_계정_회원이_탈퇴한다() {
            Long memberId = 1L;

            Member member = mock(Member.class);
            SocialCredential kakaoCredential = mock(SocialCredential.class);
            when(kakaoCredential.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(kakaoCredential.getProviderId()).thenReturn("kakao123");

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of(kakaoCredential));

            setupRequestContext(null);
            try {
                withdrawService.withdraw(memberId, null);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }

            verify(sessionManager).invalidateAllForMember(memberId);
            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
            verify(kakaoOAuth2Client).unlink("kakao123");
        }

        @Test
        void google_소셜_전용_계정_회원이_탈퇴한다() {
            Long memberId = 1L;

            Member member = mock(Member.class);
            SocialCredential googleCredential = mock(SocialCredential.class);
            when(googleCredential.getProvider()).thenReturn(SocialProvider.GOOGLE);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
            when(socialCredentialRepository.findAllByMemberId(memberId)).thenReturn(List.of(googleCredential));

            setupRequestContext("google-access-token");
            try {
                withdrawService.withdraw(memberId, null);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }

            verify(sessionManager).invalidateAllForMember(memberId);
            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
        }

        @Test
        void 소셜_로컬_계정을_모두_가지고_있는_회원이_탈퇴한다() {
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

            SocialCredential googleCredential = mock(SocialCredential.class);
            when(googleCredential.getProvider()).thenReturn(SocialProvider.GOOGLE);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
            when(socialCredentialRepository.findAllByMemberId(memberId))
                .thenReturn(List.of(kakaoCredential, googleCredential));

            setupRequestContext("google-access-token");
            try {
                withdrawService.withdraw(memberId, dto);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }

            verify(sessionManager).invalidateAllForMember(memberId);
            verify(shippingAddressRepository).deleteAllByMemberId(memberId);
            verify(notificationOptOutRepository).deleteAllByMemberId(memberId);
            verify(socialCredentialRepository).deleteByMemberId(memberId);
            verify(localCredentialRepository).deleteByMemberId(memberId);
            verify(member).withdraw();
            verify(kakaoOAuth2Client).unlink("kakao123");
        }
    }

    private void setupRequestContext(String googleAccessToken) {
        MockHttpSession session = new MockHttpSession();
        if (googleAccessToken != null) {
            session.setAttribute(AuthSessionManager.GOOGLE_ACCESS_TOKEN_ATTR, googleAccessToken);
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
