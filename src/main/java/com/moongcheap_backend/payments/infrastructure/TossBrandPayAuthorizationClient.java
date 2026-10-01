package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스페이먼츠 브랜드페이 Access Token 발급 API를 호출하는 구현체이다.
 */
@Slf4j
@Component
public class TossBrandPayAuthorizationClient implements BrandPayAuthorizationClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // 브랜드페이 API 기본 URL이 적용된 전용 HTTP 클라이언트이다.
    private final RestClient restClient;

    // 시크릿 키 뒤에 ':'을 붙여 Base64 인코딩한 Basic 인증 헤더 값이다.
    private final String authorization;

    /**
     * 공용 RestClient를 복제하여 토스페이먼츠 기본 URL과 인증 정보를 설정한다.
     * 시크릿 키는 코드나 프론트엔드에 노출하지 않고 서버 환경변수로 주입한다.
     */
    public TossBrandPayAuthorizationClient(
        RestClient restClient,
        @Value("${moongcheap.payments.brand-pay.base-url}") String baseUrl,
        @Value("${moongcheap.payments.brand-pay.secret-key:}") String secretKey
    ) {
        this.restClient = restClient.mutate().baseUrl(baseUrl).build();
        this.authorization = createBasicAuthorization(secretKey);
    }

    @Override
    public TokenResponse issue(String customerKey, String code, String idempotencyKey) {
        return requestToken(
            new AuthorizationCodeTokenRequest(customerKey, "AuthorizationCode", code),
            idempotencyKey,
            ErrorCode.BRAND_PAY_TOKEN_ISSUE_FAILED,
            "issuance"
        );
    }

    /**
     * 토스페이먼츠 토큰 발급 API에 RefreshToken 타입으로 요청한다.
     * 갱신에 성공하면 기존 토큰 대신 사용할 새로운 Access Token과 Refresh Token이 반환된다.
     */
    @Override
    public TokenResponse refresh(String customerKey, String refreshToken,
        String idempotencyKey) {
        return requestToken(
            new RefreshTokenRequest(customerKey, "RefreshToken", refreshToken),
            idempotencyKey,
            ErrorCode.BRAND_PAY_TOKEN_REFRESH_FAILED,
            "refresh"
        );
    }

    /**
     * 최초 발급과 갱신이 공유하는 HTTP 요청 및 응답 검증 로직이다.
     * 요청 종류에 맞는 예외 코드를 받아 호출자가 발급 실패와 갱신 실패를 구분할 수 있게 한다.
     */
    private TokenResponse requestToken(Object request, String idempotencyKey,
        ErrorCode failureCode, String operation) {
        // 설정 누락 상태로 외부 요청을 보내지 않고 명확한 애플리케이션 예외로 변환한다.
        if (authorization == null) {
            throw new BusinessException(
                failureCode,
                "브랜드페이 시크릿 키가 설정되지 않았습니다."
            );
        }
        // 키가 없는 요청은 중복 처리될 수 있으므로 외부 API를 호출하지 않고 실패시킨다.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(failureCode, "브랜드페이 멱등키가 생성되지 않았습니다.");
        }

        try {
            TokenResponse response = restClient.post()
                .uri("/v1/brandpay/authorizations/access-token")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TokenResponse.class);

            // HTTP 요청이 성공했더라도 필수 토큰이나 유효기간이 빠진 응답은 실패로 처리한다.
            if (!isValid(response)) {
                throw new BusinessException(failureCode);
            }
            return response;
        } catch (RestClientException exception) {
            // 토스페이먼츠의 HTTP 오류와 통신 오류가 상위 계층에 노출되지 않도록 변환한다.
            // 인증 정보가 로그에 남지 않도록 요청 및 응답 본문은 기록하지 않는다.
            log.warn("BrandPay access token {} failed", operation);
            throw new BusinessException(failureCode);
        }
    }

    /** 토큰 저장 및 재발급에 필요한 필수 응답값이 모두 존재하는지 확인한다. */
    private boolean isValid(TokenResponse response) {
        return response != null
            && response.accessToken() != null
            && !response.accessToken().isBlank()
            && response.refreshToken() != null
            && !response.refreshToken().isBlank()
            && response.expiresIn() > 0;
    }

    /** 토스페이먼츠 API 규격에 맞게 {@code Basic Base64(secretKey:)} 값을 생성한다. */
    private String createBasicAuthorization(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return null;
        }
        String credentials = Base64.getEncoder().encodeToString(
            (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + credentials;
    }

    /** 최초 발급 시 Authorization Code와 함께 전달하는 요청 본문이다. */
    private record AuthorizationCodeTokenRequest(
        String customerKey,
        String grantType,
        String code
    ) {
    }

    /** 갱신 시 기존 Refresh Token과 함께 전달하는 요청 본문이다. */
    private record RefreshTokenRequest(
        String customerKey,
        String grantType,
        String refreshToken
    ) {
    }
}
