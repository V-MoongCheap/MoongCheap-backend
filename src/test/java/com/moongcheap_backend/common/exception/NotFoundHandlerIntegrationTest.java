package com.moongcheap_backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("존재하지 않는 API 경로")
class NotFoundHandlerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SessionTestHelper sessionTestHelper;

    @Test
    @DisplayName("인증된 사용자가 정의되지 않은 경로로 요청하면 COMMON_404를 반환한다")
    void returnsCommon404ForUnknownPathWhenAuthenticated() throws Exception {
        Cookie sessionCookie = sessionTestHelper.loginAs(1L);

        mockMvc.perform(get("/api/this-path-does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @Test
    @DisplayName("미인증 사용자가 정의되지 않은 경로로 요청해도 COMMON_404를 반환한다")
    void returnsCommon404ForUnknownPathWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/this-path-does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("COMMON_404"));
    }

    @Test
    @DisplayName("미인증 사용자가 인증이 필요한 존재하는 경로로 요청하면 COMMON_401을 반환한다")
    void returnsCommon401ForExistingProtectedPathWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/categories").param("depth", "1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }
}
