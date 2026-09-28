package com.moongcheap_backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("존재하지 않는 API 경로")
class NotFoundHandlerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        sessionCookie = sessionTestHelper.loginAs(1L);
    }

    @Test
    @DisplayName("인증된 사용자가 정의되지 않은 경로로 요청하면 COMMON_404를 반환한다")
    void returnsCommon404ForUnknownPath() throws Exception {
        mockMvc.perform(get("/api/this-path-does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMON_404"));
    }
}
