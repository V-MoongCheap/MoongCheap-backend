package com.moongcheap_backend.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${moongcheap.oauth.redirect-base-url}")
    private String redirectBaseUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String provider = extractProvider(request.getRequestURI());
        String errorCode = exception instanceof OAuth2AuthenticationException oae
                ? oae.getError().getErrorCode()
                : "N/A";
        log.warn("OAuth2 login failed. provider={}, errorCode={}, message={}",
                provider, errorCode, exception.getMessage(), exception);

        String reason = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
        response.sendRedirect(redirectBaseUrl + "/oauth/failed?reason=" + reason);
    }

    // "/login/oauth2/code/kakao" → "kakao"
    private static String extractProvider(String uri) {
        if (uri == null) return "unknown";
        int idx = uri.lastIndexOf('/');
        return idx >= 0 && idx < uri.length() - 1 ? uri.substring(idx + 1) : "unknown";
    }
}
