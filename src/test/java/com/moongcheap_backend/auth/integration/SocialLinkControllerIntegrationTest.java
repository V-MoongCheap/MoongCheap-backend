package com.moongcheap_backend.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.LocalCredentialFixture;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import com.moongcheap_backend.support.integration.SocialCredentialFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SocialLinkController 통합 테스트")
class SocialLinkControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private SocialCredentialFixture socialCredentialFixture;
    @Autowired private LocalCredentialFixture localCredentialFixture;
    @Autowired private SocialCredentialRepository socialCredentialRepository;
    @Autowired private LocalCredentialRepository localCredentialRepository;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("소셜연동유저").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Nested
    @DisplayName("GET /api/auth/social-links/{provider}")
    class Link {

        @Test
        @DisplayName("지원하는 provider(kakao)로 요청하면 /oauth2/authorization/kakao 로 302 리다이렉트한다")
        void redirectsToKakaoAuthorization() throws Exception {
            mockMvc.perform(get("/api/auth/social-links/kakao").cookie(sessionCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/kakao"));
        }

        @Test
        @DisplayName("대문자 provider(GOOGLE)로 요청하면 소문자로 변환되어 리다이렉트한다")
        void redirectsUppercaseProviderToLowercase() throws Exception {
            mockMvc.perform(get("/api/auth/social-links/GOOGLE").cookie(sessionCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/google"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/auth/social-links/{provider}")
    class Unlink {

        @Test
        @DisplayName("다른 로그인 수단이 있는 상태에서 카카오 연동 해제 시 SocialCredential이 삭제된다")
        void unlinksKakaoWhenOtherCredentialExists() throws Exception {
            socialCredentialFixture.save(memberId, SocialProvider.KAKAO, "kakao-id-123");
            localCredentialFixture.saveWithPassword(memberId, "pw12345!");

            mockMvc.perform(delete("/api/auth/social-links/kakao").cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(socialCredentialRepository.findByMemberIdAndProvider(memberId,
                SocialProvider.KAKAO)).isEmpty();
        }
    }
}
