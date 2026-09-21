package com.moongcheap_backend.auth.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.LocalCredentialFixture;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("AuthController 통합 테스트")
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private LocalCredentialFixture localCredentialFixture;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("로그인된 회원이 로그아웃하면 204를 반환한다")
        void returns204OnLogout() throws Exception {
            memberId = memberFixture.save("로그아웃유저").getId();
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("DELETE /api/auth/withdraw")
    class Withdraw {

        @Test
        @DisplayName("LocalCredential 회원이 password body로 탈퇴하면 204를 반환한다")
        void withdrawsLocalMember() throws Exception {
            memberId = memberFixture.save("로컬유저").getId();
            localCredentialFixture.saveWithPassword(memberId, "password123!");
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(delete("/api/auth/withdraw")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\": \"password123!\"}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Social Credential만 있는 회원은 body 없이 탈퇴하면 204를 반환한다")
        void withdrawsSocialOnlyMember() throws Exception {
            memberId = memberFixture.save("소셜유저").getId();
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(delete("/api/auth/withdraw")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());
        }
    }
}
