package com.moongcheap_backend.payments.infrastructure;

/**
 * 브랜드페이 임시 인증 코드를 인증 토큰으로 교환하는 외부 연동 규격이다.
 * 애플리케이션 서비스가 특정 HTTP 클라이언트나 토스페이먼츠 구현에 직접 의존하지 않도록 한다.
 */
public interface BrandPayAuthorizationClient {

    /**
     * SDK 인증 과정에서 발급된 일회성 코드를 Access Token과 Refresh Token으로 교환한다.
     *
     * @param customerKey 토큰을 발급받을 구매자의 고유 식별키
     * @param code SDK 인증 완료 후 발급된 Authorization Code
     * @param idempotencyKey 동일 발급 요청을 식별하는 토스페이먼츠 멱등키
     * @return 토스페이먼츠가 발급한 인증 토큰과 Access Token 유효기간
     */
    TokenResponse issue(String customerKey, String code, String idempotencyKey);

    /**
     * 저장된 Refresh Token을 사용해 새로운 Access Token과 Refresh Token을 발급받는다.
     *
     * @param customerKey 토큰과 연결된 구매자의 고유 식별키
     * @param refreshToken 복호화된 기존 Refresh Token
     * @param idempotencyKey 동일 갱신 요청을 식별하는 토스페이먼츠 멱등키
     * @return 새로 발급된 인증 토큰과 Access Token 유효기간
     */
    TokenResponse refresh(String customerKey, String refreshToken, String idempotencyKey);

    /**
     * 외부 토큰 발급 응답을 애플리케이션 계층에 전달하기 위한 값 객체이다.
     * 토큰 원문은 영속화 전에 반드시 암호화해야 한다.
     */
    record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
    ) {
    }
}
