package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.WithdrawService;
import com.moongcheap_backend.auth.infrastructure.oauth.GoogleOAuth2Client;
import com.moongcheap_backend.auth.infrastructure.oauth.KakaoOAuth2Client;
import com.moongcheap_backend.auth.infrastructure.port.WithdrawEligibilityChecker;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.WithdrawRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class WithdrawServiceFailureTest {

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

    @BeforeEach
    void setUpRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("withdraw - 실패")
    class WithdrawFailureTest {

        @Test
        void 존재하지_않는_회원이_탈퇴를_요청한다() {
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> withdrawService.withdraw(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void 비밀번호_없이_탈퇴를_요청한다() {
            Member member = mock(Member.class);
            LocalCredential credential = mock(LocalCredential.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.of(credential));

            assertThatThrownBy(() -> withdrawService.withdraw(1L, new WithdrawRequestDto(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void 틀린_비밀번호로_탈퇴를_요청한다() {
            Member member = mock(Member.class);
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn("encoded");
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches("wrong!", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> withdrawService.withdraw(1L, new WithdrawRequestDto("wrong!")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);
        }
    }
}
