package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.AuthSignUpService;
import com.moongcheap_backend.auth.presentation.dto.LoginIdAvailabilityResponseDto;
import com.moongcheap_backend.auth.presentation.dto.SignUpRequestDto;
import com.moongcheap_backend.member.domain.LocalCredential;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthSignUpServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthSignUpService authSignUpService;

    @Nested
    @DisplayName("checkLoginId - 성공")
    class CheckLoginIdTest {

        @Test
        void 사용_중이지_않은_아이디로_중복_확인을_요청한다() {
            when(memberRepository.existsByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(false);

            LoginIdAvailabilityResponseDto result = authSignUpService.checkLoginId("user1234");

            assertThat(result.available()).isTrue();
        }

        @Test
        void 사용_중인_아이디로_중복_확인을_요청한다() {
            when(memberRepository.existsByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(true);

            LoginIdAvailabilityResponseDto result = authSignUpService.checkLoginId("user1234");

            assertThat(result.available()).isFalse();
        }
    }

    @Nested
    @DisplayName("signUp - 성공")
    class SignUpTest {

        @Test
        void 유효한_정보로_회원가입을_요청한다() {
            SignUpRequestDto dto = new SignUpRequestDto("user1234", "pass1234!", "pass1234!", "닉네임", "test@example.com");
            Member savedMember = mock(Member.class);
            when(savedMember.getId()).thenReturn(1L);
            when(memberRepository.existsByLoginIdAndDeletedAtIsNull("user1234")).thenReturn(false);
            when(memberRepository.save(any(Member.class))).thenReturn(savedMember);
            when(passwordEncoder.encode("pass1234!")).thenReturn("encoded");

            Long memberId = authSignUpService.signUp(dto);

            verify(memberRepository).save(any(Member.class));
            verify(localCredentialRepository).save(any(LocalCredential.class));
            assertThat(memberId).isEqualTo(1L);
        }
    }
}
