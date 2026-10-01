package com.moongcheap_backend.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("SocialSignupController 통합 테스트")
class SocialSignupControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("소셜가입중").getId();
        SessionPrincipal notTermsAgreed = new SessionPrincipal(
            memberId, "test-login-" + memberId, "소셜가입중",
            Set.of(MemberRole.BUYER), false, false
        );
        sessionCookie = sessionTestHelper.loginAs(notTermsAgreed);
    }

    @Test
    @DisplayName("nickname 포함 요청은 termsAgreedAt이 설정되고 닉네임이 갱신된다")
    void completesWithNickname() throws Exception {
        String body = """
            {
              "termsAgreed": true,
              "policyAgreed": true,
              "ageVerified": true,
              "nickname": "새닉네임"
            }
            """;

        mockMvc.perform(post("/api/auth/social-signup/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(sessionCookie))
            .andExpect(status().isNoContent());

        Member updated = memberRepository.findById(memberId).orElseThrow();
        assertThat(updated.getTermsAgreedAt()).isNotNull();
        assertThat(updated.getNickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("nickname 없이 요청하면 termsAgreedAt만 설정되고 기존 닉네임은 유지된다")
    void completesWithoutNickname() throws Exception {
        String body = """
            {
              "termsAgreed": true,
              "policyAgreed": true,
              "ageVerified": true
            }
            """;

        mockMvc.perform(post("/api/auth/social-signup/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(sessionCookie))
            .andExpect(status().isNoContent());

        Member updated = memberRepository.findById(memberId).orElseThrow();
        assertThat(updated.getTermsAgreedAt()).isNotNull();
        assertThat(updated.getNickname()).isEqualTo("소셜가입중");
    }
}
