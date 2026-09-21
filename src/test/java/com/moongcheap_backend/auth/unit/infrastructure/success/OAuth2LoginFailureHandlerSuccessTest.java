package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.oauth.OAuth2LoginFailureHandler;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

class OAuth2LoginFailureHandlerSuccessTest {

    private OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginFailureHandler();
        ReflectionTestUtils.setField(handler, "redirectBaseUrl", "http://localhost:3000");
    }

    @Nested
    @DisplayName("onAuthenticationFailure - 성공")
    class OnAuthenticationFailureTest {

        @Test
        void OAuth2_로그인에_실패한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            AuthenticationException exception = mock(AuthenticationException.class);
            when(exception.getMessage()).thenReturn("소셜 로그인 실패");

            handler.onAuthenticationFailure(request, response, exception);

            String encoded = URLEncoder.encode("소셜 로그인 실패", StandardCharsets.UTF_8);
            assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/failed?reason=" + encoded);
        }
    }
}
