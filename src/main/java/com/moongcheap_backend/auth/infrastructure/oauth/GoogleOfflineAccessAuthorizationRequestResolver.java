package com.moongcheap_backend.auth.infrastructure.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

/**
 * Google refresh_token 발급을 위해 authorization request 에 access_type=offline / prompt=consent 를 주입한다.
 * Kakao 등 다른 provider 는 기본 파라미터로 통과시킨다.
 */
@Component
public class GoogleOfflineAccessAuthorizationRequestResolver
    implements OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    private static final String GOOGLE_REGISTRATION_ID = "google";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleOfflineAccessAuthorizationRequestResolver(
        ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, AUTHORIZATION_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request,
        String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req) {
        if (req == null) {
            return null;
        }
        String registrationId = req.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        if (!GOOGLE_REGISTRATION_ID.equalsIgnoreCase(registrationId)) {
            return req;
        }
        return OAuth2AuthorizationRequest.from(req)
            .additionalParameters(params -> {
                params.put("access_type", "offline");
                params.put("prompt", "consent");
            })
            .build();
    }
}
