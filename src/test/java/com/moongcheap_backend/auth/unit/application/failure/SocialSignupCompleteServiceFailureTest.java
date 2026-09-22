package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.NicknameService;
import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.auth.application.SocialSignupCompleteService;
import com.moongcheap_backend.auth.presentation.dto.SocialSignupCompleteRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialSignupCompleteServiceFailureTest {

    @Mock private MemberRepository memberRepository;
    @Mock private NicknameService nicknameService;
    @Mock private PrincipalFactory principalFactory;

    @InjectMocks
    private SocialSignupCompleteService socialSignupCompleteService;

    @Nested
    @DisplayName("complete - 실패")
    class CompleteFailureTest {

        @Test
        void 존재하지_않는_회원이_소셜_가입_완료를_요청한다() {
            SocialSignupCompleteRequestDto dto = new SocialSignupCompleteRequestDto(true, true, true, null);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> socialSignupCompleteService.complete(1L, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        void 이미_가입_완료된_회원이_소셜_가입_완료를_요청한다() {
            SocialSignupCompleteRequestDto dto = new SocialSignupCompleteRequestDto(true, true, true, null);
            Member member = mock(Member.class);
            when(member.isTermsAgreed()).thenReturn(true);
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

            assertThatThrownBy(() -> socialSignupCompleteService.complete(1L, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_SIGNUP_ALREADY_COMPLETE);
        }

        @Test
        void 이미_사용_중인_닉네임으로_소셜_가입을_완료한다() {
            SocialSignupCompleteRequestDto dto = new SocialSignupCompleteRequestDto(true, true, true, "중복닉네임");
            Member member = mock(Member.class);
            when(member.isTermsAgreed()).thenReturn(false);
            when(member.getNickname()).thenReturn("다른닉네임");
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
            doThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATED))
                .when(nicknameService).ensureAvailable("중복닉네임");

            assertThatThrownBy(() -> socialSignupCompleteService.complete(1L, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
        }
    }
}
