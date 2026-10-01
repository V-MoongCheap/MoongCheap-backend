package com.moongcheap_backend.auth.unit.infrastructure.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.oauth.CustomOAuth2UserService;
import com.moongcheap_backend.auth.infrastructure.oauth.OAuth2LoginSuccessHandler;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerFailureTest {

    @Mock AuthSessionManager sessionManager;
    @Mock OAuth2AuthorizedClientRepository authorizedClientRepository;

    @InjectMocks
    OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "redirectBaseUrl", "http://localhost:3000");
    }

    @Nested
    @DisplayName("onAuthenticationSuccess - 실패")
    class OnAuthenticationSuccessFailureTest {

        @Test
        void SessionPrincipal_속성이_없는_상태에서_소셜_로그인이_성공_처리된다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(null);

            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(oauth2User);

            assertThatThrownBy(() -> handler.onAuthenticationSuccess(request, response, authentication))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
