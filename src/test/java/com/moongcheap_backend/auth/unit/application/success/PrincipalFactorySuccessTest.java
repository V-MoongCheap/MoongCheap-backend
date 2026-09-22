package com.moongcheap_backend.auth.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.PrincipalFactory;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PrincipalFactorySuccessTest {

    private final PrincipalFactory principalFactory = new PrincipalFactory();

    @Nested
    @DisplayName("build - 성공")
    class BuildTest {

        @Test
        void 일반_사용자의_session이_생성된다() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(1L);
            when(member.getLoginId()).thenReturn("user1234");
            when(member.getNickname()).thenReturn("닉네임");
            when(member.isSeller()).thenReturn(false);
            when(member.isTermsAgreed()).thenReturn(true);

            SessionPrincipal result = principalFactory.build(member);

            assertAll(
                () -> assertThat(result.memberId()).isEqualTo(1L),
                () -> assertThat(result.loginId()).isEqualTo("user1234"),
                () -> assertThat(result.nickname()).isEqualTo("닉네임"),
                () -> assertThat(result.roles()).containsExactly(MemberRole.BUYER),
                () -> assertThat(result.sellerApproved()).isFalse()
            );
        }

        @Test
        void 판매자의_session이_생성된다() {
            Member member = mock(Member.class);
            when(member.getId()).thenReturn(2L);
            when(member.getLoginId()).thenReturn("seller1234");
            when(member.getNickname()).thenReturn("판매자닉");
            when(member.isSeller()).thenReturn(true);
            when(member.isTermsAgreed()).thenReturn(true);

            SessionPrincipal result = principalFactory.build(member);

            assertAll(
                () -> assertThat(result.memberId()).isEqualTo(2L),
                () -> assertThat(result.roles()).contains(MemberRole.BUYER, MemberRole.SELLER),
                () -> assertThat(result.sellerApproved()).isTrue()
            );
        }
    }
}
