package com.moongcheap_backend.auth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("IncompleteSignupFilter 통합 테스트")
class IncompleteSignupFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie termsNotAgreedCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        Long memberId = memberFixture.save("가입중").getId();
        termsNotAgreedCookie = sessionTestHelper.loginAs(new SessionPrincipal(
            memberId, "login-" + memberId, "가입중",
            Set.of(MemberRole.BUYER), false, false
        ));
    }

    @Test
    @DisplayName("termsAgreed=false 세션이 허용 경로(logout)를 호출하면 정상 처리된다")
    void allowsWhitelistedPathForUnfinishedSignup() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(termsNotAgreedCookie))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("termsAgreed=false 세션이 비허용 경로(/api/members/me)를 호출하면 SOCIAL_SIGNUP_INCOMPLETE 에러가 반환된다")
    void blocksNonWhitelistedPathForUnfinishedSignup() throws Exception {
        mockMvc.perform(get("/api/members/me").cookie(termsNotAgreedCookie))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("AUTH_016"));
    }
}
