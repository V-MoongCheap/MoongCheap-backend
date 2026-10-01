package com.moongcheap_backend.auth.infrastructure.oauth;

import com.moongcheap_backend.auth.application.GoogleRefreshTokenSyncService;
import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String GOOGLE_REGISTRATION_ID = "google";

    private final AuthSessionManager sessionManager;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final GoogleRefreshTokenSyncService googleRefreshTokenSyncService;

    @Value("${moongcheap.oauth.redirect-base-url}")
    private String redirectBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        SessionPrincipal principal = oauth2User.getAttribute(CustomOAuth2UserService.ATTR_PRINCIPAL);
        if (principal == null) {
            throw new IllegalStateException("OAuth2 로그인 후 SessionPrincipal 속성을 찾지 못했습니다.");
        }

        sessionManager.bindPrincipal(request, principal, false);

        persistGoogleRefreshTokenIfApplicable(authentication, request, principal.memberId());

        boolean termsAgreed = Boolean.TRUE.equals(oauth2User.getAttribute(CustomOAuth2UserService.ATTR_TERMS_AGREED));
        String redirectUrl = termsAgreed
                ? redirectBaseUrl + "/oauth/callback"
                : redirectBaseUrl + "/oauth/callback?status=incomplete";
        response.sendRedirect(redirectUrl);
    }

    /**
     * Google 로그인일 때만 refresh_token 을 확보해 DB 에 저장한다.
     * 저장 실패는 로그인 자체를 막지 않는다 - 다음 로그인 (prompt=consent) 에서 재확보 가능.
     */
    private void persistGoogleRefreshTokenIfApplicable(Authentication authentication,
        HttpServletRequest request, Long memberId) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Auth)) return;
        if (!GOOGLE_REGISTRATION_ID.equalsIgnoreCase(
            oauth2Auth.getAuthorizedClientRegistrationId())) return;

        OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
            oauth2Auth.getAuthorizedClientRegistrationId(), oauth2Auth, request);
        if (client == null) return;

        OAuth2RefreshToken refreshToken = client.getRefreshToken();
        if (refreshToken == null) {
            log.warn("Google refresh_token missing for memberId={}. "
                + "access_type=offline / prompt=consent 확인 필요", memberId);
            return;
        }

        try {
            googleRefreshTokenSyncService.updateRefreshToken(memberId, refreshToken.getTokenValue());
        } catch (Exception e) {
            log.warn("Failed to persist Google refresh_token memberId={}", memberId, e);
        }
    }
}
