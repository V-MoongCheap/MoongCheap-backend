package com.moongcheap_backend.auth.unit.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.NicknameService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknameServiceFailureTest {

    @Mock private MemberRepository memberRepository;

    @InjectMocks
    private NicknameService nicknameService;

    @Nested
    @DisplayName("ensureAvailable - 실패")
    class EnsureAvailableFailureTest {

        @Test
        void 사용중인_닉네임을_입력한다() {
            when(memberRepository.existsByNicknameAndDeletedAtIsNull("닉네임")).thenReturn(true);

            assertThatThrownBy(() -> nicknameService.ensureAvailable("닉네임"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    @Nested
    @DisplayName("allocateForSocial - 실패")
    class AllocateForSocialFailureTest {

        @Test
        void _10개의_후보_모두가_사용_중인_소셜_닉네임을_발급한다() {
            when(memberRepository.findTakenNicknames(any())).thenAnswer(invocation -> {
                java.util.Collection<String> candidates = invocation.getArgument(0);
                return Set.copyOf(candidates);
            });

            assertThatThrownBy(() -> nicknameService.allocateForSocial("홍길동"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
        }
    }
}
