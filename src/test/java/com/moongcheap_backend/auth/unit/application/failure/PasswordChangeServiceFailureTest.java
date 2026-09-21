package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.PasswordChangeService;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.ChangePasswordRequestDto;
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
class PasswordChangeServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionManager sessionManager;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    @Nested
    @DisplayName("changePassword - 실패")
    class ChangePasswordFailureTest {

        @Test
        void 새_비밀번호와_새_비밀번호_확인이_다른_값으로_비밀번호_변경을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            ChangePasswordRequestDto dto = new ChangePasswordRequestDto("current1!", "newPass2@", "different3#");

            assertThatThrownBy(() -> passwordChangeService.changePassword(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        }

        @Test
        void 존재하지_않는_회원이_비밀번호_변경을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            ChangePasswordRequestDto dto = new ChangePasswordRequestDto("current1!", "newPass2@", "newPass2@");
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> passwordChangeService.changePassword(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void 소셜_전용_계정_회원이_비밀번호_변경을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            ChangePasswordRequestDto dto = new ChangePasswordRequestDto("current1!", "newPass2@", "newPass2@");
            Member member = mock(Member.class);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> passwordChangeService.changePassword(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCAL_CREDENTIAL_REQUIRED);
        }

        @Test
        void 현재_비밀번호가_틀린_상태에서_비밀번호_변경을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            ChangePasswordRequestDto dto = new ChangePasswordRequestDto("wrong!", "newPass2@", "newPass2@");
            Member member = mock(Member.class);
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn("encoded");
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches("wrong!", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> passwordChangeService.changePassword(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        @Test
        void 이전과_동일한_비밀번호로_변경을_요청한다() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            ChangePasswordRequestDto dto = new ChangePasswordRequestDto("same1234!", "same1234!", "same1234!");
            Member member = mock(Member.class);
            when(member.getLoginId()).thenReturn("user1234");
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn("encoded");
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(1L)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches("same1234!", "encoded")).thenReturn(true);

            assertThatThrownBy(() -> passwordChangeService.changePassword(1L, dto, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_SAME_AS_PREVIOUS);
        }
    }
}
