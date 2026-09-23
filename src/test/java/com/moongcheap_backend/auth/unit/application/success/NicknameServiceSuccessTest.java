package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.NicknameService;
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
class NicknameServiceSuccessTest {

    @Mock private MemberRepository memberRepository;

    @InjectMocks
    private NicknameService nicknameService;

    @Nested
    @DisplayName("isAvailable - 성공")
    class IsAvailableTest {

        @Test
        void 사용_중이지_않은_닉네임을_입력한다() {
            when(memberRepository.existsByNicknameAndDeletedAtIsNull("닉네임")).thenReturn(false);

            boolean result = nicknameService.isAvailable("닉네임");

            assertThat(result).isTrue();
        }

        @Test
        void 사용_중인_닉네임을_입력한다() {
            when(memberRepository.existsByNicknameAndDeletedAtIsNull("닉네임")).thenReturn(true);

            boolean result = nicknameService.isAvailable("닉네임");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("ensureAvailable - 성공")
    class EnsureAvailableTest {

        @Test
        void 사용_중이지_않은_닉네임을_입력한다() {
            when(memberRepository.existsByNicknameAndDeletedAtIsNull("닉네임")).thenReturn(false);

            assertThatCode(() -> nicknameService.ensureAvailable("닉네임"))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("allocateForSocial - 성공")
    class AllocateForSocialTest {

        @Test
        void candidate가_정상_닉네임일_때_소셜_닉네임을_발급한다() {
            when(memberRepository.findTakenNicknames(any())).thenReturn(Set.of());

            String result = nicknameService.allocateForSocial("홍길동");

            assertThat(result).startsWith("홍길동");
            assertThat(result).isNotEqualTo("홍길동");
            assertThat(result).matches("홍길동\\d+");
        }

        @Test
        void base가_이미_사용_중일_때_소셜_닉네임을_발급한다() {
            when(memberRepository.findTakenNicknames(any())).thenAnswer(invocation -> {
                java.util.Collection<String> candidates = invocation.getArgument(0);
                String first = candidates.iterator().next();
                return Set.of(first);
            });

            String result = nicknameService.allocateForSocial("홍길동");

            assertThat(result).isNotEqualTo("홍길동");
            assertThat(result).startsWith("홍길동");
        }

        @Test
        void candidate가_null일_때_임의의_소셜_닉네임을_발급한다() {
            when(memberRepository.findTakenNicknames(any())).thenReturn(Set.of());

            String result = nicknameService.allocateForSocial(null);

            assertThat(result).startsWith("user");
        }

        @Test
        void candidate가_공백일_때_임의의_소셜_닉네임을_발급한다() {
            when(memberRepository.findTakenNicknames(any())).thenReturn(Set.of());

            String result = nicknameService.allocateForSocial("   ");

            assertThat(result).startsWith("user");
        }

        @Test
        void base가_20자일_때_suffix를_붙인_결과가_20자를_초과하지_않는다() {
            when(memberRepository.findTakenNicknames(any())).thenAnswer(invocation -> {
                java.util.Collection<String> candidates = invocation.getArgument(0);
                String first = candidates.iterator().next();
                return Set.of(first);
            });

            String base = "12345678901234567890"; // 20자
            String result = nicknameService.allocateForSocial(base);

            assertThat(result.length()).isLessThanOrEqualTo(20);
        }
    }
}
