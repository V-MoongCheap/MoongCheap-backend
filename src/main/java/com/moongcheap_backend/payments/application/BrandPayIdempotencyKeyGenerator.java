package com.moongcheap_backend.payments.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 브랜드페이 토큰 발급·갱신 요청에 사용할 토스페이먼츠 멱등키를 생성한다.
 * 원본 인증 코드나 암호화된 토큰은 헤더에 직접 노출하지 않고 SHA-256 결과만 사용한다.
 */
@Component
public class BrandPayIdempotencyKeyGenerator {

    private static final String SHA_256 = "SHA-256";

    /**
     * 최초 발급 재시도에서 같은 Authorization Code가 같은 멱등키를 만들도록 한다.
     * 용도 문자열을 포함해 갱신 요청의 멱등키 영역과 분리한다.
     */
    public String forIssue(Long memberId, String authorizationCode) {
        return sha256("brandpay-issue-v1:" + memberId + ":" + authorizationCode);
    }

    /**
     * 현재 DB에 저장된 암호화 Access Token이 같으면 동일한 멱등키를 생성한다.
     * 토스 요청 성공 후 DB 저장에 실패해도 암호문이 그대로이므로 같은 응답을 복구할 수 있다.
     */
    public String forRefresh(Long memberId, String encryptedAccessToken) {
        return sha256("brandpay-refresh-v1:" + memberId + ":" + encryptedAccessToken);
    }

    private String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256)
                .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256은 모든 Java 구현이 반드시 제공하므로 발생하면 실행 환경 오류다.
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
