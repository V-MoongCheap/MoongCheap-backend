package com.moongcheap_backend.auth.unit.infrastructure.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.GoogleRefreshTokenSyncService;
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
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerSuccessTest {

    @Mock AuthSessionManager sessionManager;
    @Mock OAuth2AuthorizedClientRepository authorizedClientRepository;
    @Mock GoogleRefreshTokenSyncService googleRefreshTokenSyncService;

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
        void Google_로그인_성공_시_refresh_token이_sync_service로_위임된다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(principal);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED)).thenReturn(true);
            doReturn(List.of(new SimpleGrantedAuthority("ROLE_BUYER")))
                .when(oauth2User).getAuthorities();

            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oauth2User, oauth2User.getAuthorities(), "google");

            OAuth2RefreshToken refreshToken = mock(OAuth2RefreshToken.class);
            when(refreshToken.getTokenValue()).thenReturn("plain-refresh-token");
            OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
            when(client.getRefreshToken()).thenReturn(refreshToken);
            when(authorizedClientRepository.loadAuthorizedClient(eq("google"), any(), any()))
                .thenReturn(client);

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(googleRefreshTokenSyncService).updateRefreshToken(1L, "plain-refresh-token");
        }

        @Test
        void Google_로그인이지만_refresh_token이_없는_경우_sync_service가_호출되지_않는다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(principal);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED)).thenReturn(true);
            doReturn(List.of(new SimpleGrantedAuthority("ROLE_BUYER")))
                .when(oauth2User).getAuthorities();

            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oauth2User, oauth2User.getAuthorities(), "google");

            OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
            when(client.getRefreshToken()).thenReturn(null);
            when(authorizedClientRepository.loadAuthorizedClient(eq("google"), any(), any()))
                .thenReturn(client);

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(googleRefreshTokenSyncService, never()).updateRefreshToken(any(), any());
        }

        @Test
        void sync_service가_예외를_던져도_로그인은_계속_성공한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            OAuth2User oauth2User = mock(OAuth2User.class);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL)).thenReturn(principal);
            when(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED)).thenReturn(true);
            doReturn(List.of(new SimpleGrantedAuthority("ROLE_BUYER")))
                .when(oauth2User).getAuthorities();

            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oauth2User, oauth2User.getAuthorities(), "google");

            OAuth2RefreshToken refreshToken = mock(OAuth2RefreshToken.class);
            when(refreshToken.getTokenValue()).thenReturn("plain-refresh-token");
            OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
            when(client.getRefreshToken()).thenReturn(refreshToken);
            when(authorizedClientRepository.loadAuthorizedClient(eq("google"), any(), any()))
                .thenReturn(client);
            org.mockito.Mockito.doThrow(new RuntimeException("db failure"))
                .when(googleRefreshTokenSyncService).updateRefreshToken(1L, "plain-refresh-token");

            handler.onAuthenticationSuccess(request, response, authentication);

            // 로그인 자체는 성공: redirect 가 실행됨
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback");
        }
    }
}
