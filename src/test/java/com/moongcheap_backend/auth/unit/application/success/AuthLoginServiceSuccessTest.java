package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.AuthLoginService;
import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.infrastructure.session.LoginFailureCounter;
import com.moongcheap_backend.auth.presentation.dto.LoginRequestDto;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginFailureCounter failureCounter;
    @Mock private AuthSessionManager sessionManager;
    @Mock private PrincipalFactory principalFactory;

    @InjectMocks
    private AuthLoginService authLoginService;

    @Nested
    @DisplayName("logout - 성공")
    class LogoutTest {

        @Test
        void 사용자가_로그아웃을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);

            authLoginService.logout(request);

            verify(sessionManager).invalidateCurrent(request);
        }
    }

    @Nested
    @DisplayName("login - 성공")
    class LoginTest {

        @Test
        void 유효한_아이디와_비밀번호로_로그인을_요청한다() {
            String loginId = "user1234";
            String rawPassword = "pass1234!";
            String encodedPassword = "encoded";
            HttpServletRequest request = mock(HttpServletRequest.class);
            LoginRequestDto dto = new LoginRequestDto(loginId, rawPassword, false);

            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn(encodedPassword);

            SessionPrincipal principal = new SessionPrincipal(1L, loginId, "nick", Set.of(MemberRole.BUYER), false, true);

            when(failureCounter.isLocked(loginId)).thenReturn(false);
            when(memberRepository.findByLoginIdAndDeletedAtIsNull(loginId)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(member.getId())).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
            when(principalFactory.build(member)).thenReturn(principal);

            SessionPrincipal result = authLoginService.login(dto, request);

            verify(failureCounter).reset(loginId);
            verify(sessionManager).bindPrincipal(request, principal, false);
            assertThat(result).isEqualTo(principal);
        }
    }
}
