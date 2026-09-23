package com.moongcheap_backend.payments.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브랜드페이 SDK가 인증 콜백으로 전달한 값을 토큰 발급 서비스에 전달하는 요청이다.
 * 시크릿 키와 발급된 토큰은 프론트엔드에서 다루지 않는다.
 */
public record BrandPayAuthorizationRequest(
    // SDK 인증에 사용한 구매자 식별키로, 로그인 회원에게 발급된 값과 일치해야 한다.
    @NotBlank
    @Size(min = 2, max = 50)
    String customerKey,

    // 토큰 발급에 한 번만 사용할 수 있는 임시 Authorization Code이다.
    @NotBlank
    String code
) {
}
