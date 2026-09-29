package com.moongcheap_backend.auth.infrastructure.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoOAuth2Client {

    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

    private final RestClient restClient;
    private final String adminKey;

    public KakaoOAuth2Client(RestClient restClient,
                             @Value("${moongcheap.oauth2.kakao.admin-key:}") String adminKey) {
        this.restClient = restClient;
        this.adminKey = adminKey;
    }

    /**
     * Kakao 사용자 연결 끊기.
     * - 2xx : 성공
     * - 400/404 : 이미 연결 해제된 사용자 또는 존재하지 않는 target 으로 간주하여 정상 종료.
     *            (결과적으로 unlink 상태이므로 재시도할 이유가 없음)
     * - 그 외 (401/403/429/5xx 등) : 예외 전파. 워커가 재시도한다.
     */
    public void unlink(String providerId) {
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("Kakao admin key not configured; skipping unlink providerId={}", providerId);
            return;
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("target_id_type", "user_id");
        body.add("target_id", providerId);

        try {
            restClient.post()
                .uri(UNLINK_URL)
                .header("Authorization", "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.info("Kakao unlink success providerId={}", providerId);
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound e) {
            log.info("Kakao unlink: target invalid or already unlinked, treating as success"
                + " providerId={} status={}", providerId, e.getStatusCode().value());
        }
    }
}
