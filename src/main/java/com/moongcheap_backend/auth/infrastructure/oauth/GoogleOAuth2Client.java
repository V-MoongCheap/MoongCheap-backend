package com.moongcheap_backend.auth.infrastructure.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuth2Client {

    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private final RestClient restClient;

    /**
     * Google 토큰(access 또는 refresh) 을 revoke 한다.
     * - 2xx : 성공
     * - 400/404 : 이미 revoke 되었거나 만료된 토큰. 결과적으로 unlink 상태이므로 정상 종료.
     * - 그 외 (401/403/429/5xx 등) : 예외 전파. 워커가 재시도한다.
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Google token not available; skipping revoke");
            return;
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        try {
            restClient.post()
                .uri(REVOKE_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.info("Google revoke success");
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound e) {
            log.info("Google revoke: token invalid or not found, treating as success status={}",
                e.getStatusCode().value());
        }
    }
}
