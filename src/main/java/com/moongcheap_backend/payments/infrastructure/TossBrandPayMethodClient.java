package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 토스페이먼츠의 Access Token 기반 결제수단 조회 API 구현체다. */
@Slf4j
@Component
public class TossBrandPayMethodClient implements BrandPayMethodClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;

    public TossBrandPayMethodClient(
        RestClient restClient,
        @Value("${moongcheap.payments.brand-pay.base-url}") String baseUrl
    ) {
        this.restClient = restClient.mutate().baseUrl(baseUrl).build();
    }

    @Override
    public MethodsResponse getAll(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_ACCESS_TOKEN_UNAVAILABLE);
        }

        try {
            MethodsResponse response = restClient.get()
                .uri("/v1/brandpay/payments/methods")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(MethodsResponse.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_LOOKUP_FAILED);
            }
            return response;
        } catch (RestClientException exception) {
            // Bearer 토큰과 결제수단 정보가 로그에 남지 않도록 상태나 본문은 기록하지 않는다.
            log.warn("BrandPay payment method lookup failed");
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_LOOKUP_FAILED);
        }
    }

    @Override
    public void remove(String accessToken, String methodKey, PaymentType type,
        String idempotencyKey) {
        validateRemoveRequest(accessToken, methodKey, type, idempotencyKey);

        String path = switch (type) {
            case CARD -> "/v1/brandpay/payments/methods/card/remove";
            case ACCOUNT -> "/v1/brandpay/payments/methods/account/remove";
        };

        try {
            restClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RemoveRequest(methodKey))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException exception) {
            // 토큰과 methodKey가 로그에 남지 않도록 외부 오류의 상세 본문은 기록하지 않는다.
            log.warn("BrandPay payment method removal failed: type={}", type);
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_REMOVE_FAILED);
        }
    }

    private void validateRemoveRequest(String accessToken, String methodKey,
        PaymentType type, String idempotencyKey) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_ACCESS_TOKEN_UNAVAILABLE);
        }
        if (methodKey == null || methodKey.isBlank() || type == null
            || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.BRAND_PAY_METHOD_REMOVE_FAILED);
        }
    }

    private record RemoveRequest(String methodKey) {
    }
}
