package com.moongcheap_backend.auth.unit.application.success;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.NicknameService;
import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.application.SocialSignupCompleteService;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.SocialSignupCompleteRequestDto;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
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

@ExtendWith(MockitoExtension.class)
class SocialSignupCompleteServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private NicknameService nicknameService;
    @Mock private PrincipalFactory principalFactory;
    @Mock private AuthSessionManager sessionManager;

    @InjectMocks
    private SocialSignupCompleteService socialSignupCompleteService;

    @Nested
    @DisplayName("complete - 성공")
    class CompleteTest {

        @Test
        void 닉네임_없이_소셜_가입을_완료한다() {
            Long memberId = 1L;
            SocialSignupCompleteRequestDto dto = new SocialSignupCompleteRequestDto(true, true, true, null);
            HttpServletRequest request = mock(HttpServletRequest.class);

            Member member = mock(Member.class);
            when(member.isTermsAgreed()).thenReturn(false);
            SessionPrincipal principal = mock(SessionPrincipal.class);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(principalFactory.build(member)).thenReturn(principal);

            socialSignupCompleteService.complete(memberId, dto, request);

            verify(member).agreeTerms();
            verify(sessionManager).refreshPrincipal(request, principal);
            verify(member, never()).changeProfile(any(), any(), any(), any());
        }

        @Test
        void 새로운_닉네임으로_소셜_가입을_완료한다() {
            Long memberId = 1L;
            String newNickname = "새닉네임";
            SocialSignupCompleteRequestDto dto = new SocialSignupCompleteRequestDto(true, true, true, newNickname);
            HttpServletRequest request = mock(HttpServletRequest.class);

            Member member = mock(Member.class);
            when(member.isTermsAgreed()).thenReturn(false);
            when(member.getNickname()).thenReturn("기존닉네임");
            SessionPrincipal principal = mock(SessionPrincipal.class);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(principalFactory.build(member)).thenReturn(principal);

            socialSignupCompleteService.complete(memberId, dto, request);

            verify(nicknameService).ensureAvailable("새닉네임");
            verify(member).changeProfile(eq("새닉네임"), any(), any(), any());
            verify(member).agreeTerms();
            verify(sessionManager).refreshPrincipal(request, principal);
        }
    }
}
