package com.moongcheap_backend.auth.unit.application.success;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.PasswordChangeService;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.auth.presentation.dto.ChangePasswordRequestDto;
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
class PasswordChangeServiceSuccessTest {

    @Mock private MemberRepository memberRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionManager sessionManager;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    @Nested
    @DisplayName("changePassword - 성공")
    class ChangePasswordTest {

        @Test
        void 유효한_정보로_비밀번호를_변경한다() {
            Long memberId = 1L;
            String currentRaw = "current1!";
            String newRaw = "newPass2@";
            String encodedCurrent = "encodedCurrent";
            String encodedNew = "encodedNew";
            ChangePasswordRequestDto dto = new ChangePasswordRequestDto(currentRaw, newRaw, newRaw);
            HttpServletRequest request = mock(HttpServletRequest.class);

            Member member = mock(Member.class);
            when(member.getLoginId()).thenReturn("user1234");
            LocalCredential credential = mock(LocalCredential.class);
            when(credential.getPassword()).thenReturn(encodedCurrent);

            when(memberRepository.findByIdAndDeletedAtIsNull(memberId)).thenReturn(Optional.of(member));
            when(localCredentialRepository.findByMemberId(memberId)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(currentRaw, encodedCurrent)).thenReturn(true);
            when(passwordEncoder.matches(newRaw, encodedCurrent)).thenReturn(false);
            when(passwordEncoder.encode(newRaw)).thenReturn(encodedNew);

            passwordChangeService.changePassword(memberId, dto, request);

            verify(credential).changePassword(encodedNew);
            verify(sessionManager).invalidateAllExceptCurrent(memberId, request);
        }
    }
}
