package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.infrastructure.oauth.CustomOAuth2UserService;
import com.moongcheap_backend.auth.infrastructure.oauth.OAuth2LoginSuccessHandler;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.MemberRole;
import com.moongcheap_backend.common.security.SessionPrincipal;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerSuccessTest {

    @Mock AuthSessionManager sessionManager;
    @Mock OAuth2AuthorizedClientRepository authorizedClientRepository;

    @InjectMocks
    OAuth2LoginSuccessHandler handler;

    private SessionPrincipal principal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "redirectBaseUrl", "http://localhost:3000");
        principal = new SessionPrincipal(1L, "user1234", "닉네임", Set.of(MemberRole.BUYER), false, true);
    }

    @Nested
    @DisplayName("onAuthenticationSuccess - 성공")
    class OnAuthenticationSuccessTest {

        @Test
        void 약관_동의_완료_회원이_소셜_로그인에_성공한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(principal);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED)).thenReturn(true);

            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(oauth2User);

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(sessionManager).bindPrincipal(request, principal, false);
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback");
        }

        @Test
        void 약관_미동의_회원이_소셜_로그인에_성공한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            SessionPrincipal incompletePrincipal = new SessionPrincipal(2L, null, "닉네임", Set.of(MemberRole.BUYER), false, false);
            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(incompletePrincipal);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED)).thenReturn(false);

            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(oauth2User);

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(sessionManager).bindPrincipal(request, incompletePrincipal, false);
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?status=incomplete");
        }

        @Test
        void Google_로그인_성공_시_access_token이_세션에_저장된다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(principal);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED)).thenReturn(true);
            doReturn(List.of(new SimpleGrantedAuthority("ROLE_BUYER")))
                .when(oauth2User).getAuthorities();

            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oauth2User, oauth2User.getAuthorities(), "google");

            OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
            when(accessToken.getTokenValue()).thenReturn("google-access-token");
            OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
            when(client.getAccessToken()).thenReturn(accessToken);
            when(authorizedClientRepository.loadAuthorizedClient(eq("google"), any(), any()))
                .thenReturn(client);

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(request.getSession().getAttribute(AuthSessionManager.GOOGLE_ACCESS_TOKEN_ATTR))
                .isEqualTo("google-access-token");
        }
    }
}
