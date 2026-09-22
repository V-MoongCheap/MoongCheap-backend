package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.AuthLoginService;
import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.infrastructure.session.LoginFailureCounter;
import com.moongcheap_backend.auth.presentation.dto.LoginRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginFailureCounter failureCounter;
    @Mock private AuthSessionManager sessionManager;
    @Mock private PrincipalFactory principalFactory;

    @InjectMocks
    private AuthLoginService authLoginService;

    @Nested
    @DisplayName("login - 실패")
    class LoginFailureTest {

        @Test
        void 잠긴_계정으로_로그인을_시도한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            LoginRequestDto dto = new LoginRequestDto("user1234", "pass1234!", false);
            when(failureCounter.isLocked("user1234")).thenReturn(true);

            assertThatThrownBy(() -> authLoginService.login(dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_LOCKED);
        }

        @Test
        void 존재하지_않는_아이디로_로그인을_시도한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            LoginRequestDto dto = new LoginRequestDto("user1234", "pass1234!", false);
            when(failureCounter.isLocked("user1234")).thenReturn(false);
            when(memberRepository.findByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authLoginService.login(dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        @Test
        void 로컬_credential이_없는_계정으로_로그인을_시도한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            LoginRequestDto dto = new LoginRequestDto("user1234", "pass1234!", false);
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(failureCounter.isLocked("user1234")).thenReturn(false);
            when(memberRepository.findByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authLoginService.login(dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);

            verify(failureCounter).recordFailure("user1234");
        }

        @Test
        void 틀린_비밀번호로_로그인을_시도한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            LoginRequestDto dto = new LoginRequestDto("user1234", "wrongPass!", false);
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn("encoded");
            when(failureCounter.isLocked("user1234")).thenReturn(false);
            when(memberRepository.findByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches("wrongPass!", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authLoginService.login(dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);

            verify(failureCounter).recordFailure("user1234");
        }
    }
}
