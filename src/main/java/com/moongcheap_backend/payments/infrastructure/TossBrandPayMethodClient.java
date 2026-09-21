package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 토스페이먼츠의 Access Token 기반 결제수단 조회 API 구현체다. */
@Slf4j
@Component
public class TossBrandPayMethodClient implements BrandPayMethodClient {

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
}
