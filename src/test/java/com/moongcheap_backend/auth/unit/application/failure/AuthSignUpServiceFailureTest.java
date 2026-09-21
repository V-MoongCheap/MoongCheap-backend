package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.AuthSignUpService;
import com.moongcheap_backend.auth.presentation.dto.SignUpRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthSignUpServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthSignUpService authSignUpService;

    @Nested
    @DisplayName("signUp - 실패")
    class SignUpFailureTest {

        @Test
        void 비밀번호와_비밀번호_확인이_다른_값으로_회원가입을_요청한다() {
            SignUpRequestDto dto = new SignUpRequestDto("user1234", "pass1234!", "different!", "닉네임", "test@example.com");

            assertThatThrownBy(() -> authSignUpService.signUp(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        }

        @Test
        void 이미_사용_중인_아이디로_회원가입을_요청한다() {
            SignUpRequestDto dto = new SignUpRequestDto("user1234", "pass1234!", "pass1234!", "닉네임", "test@example.com");
            when(memberRepository.existsByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(true);

            assertThatThrownBy(() -> authSignUpService.signUp(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_ID_DUPLICATED);
        }

        @Test
        void 동시에_동일한_아이디로_회원가입을_요청한다() {
            SignUpRequestDto dto = new SignUpRequestDto("user1234", "pass1234!", "pass1234!", "닉네임", "test@example.com");
            when(memberRepository.existsByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(false);
            when(memberRepository.save(any(Member.class))).thenThrow(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> authSignUpService.signUp(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONCURRENT_SIGNUP_CONFLICT);
        }
    }
}
