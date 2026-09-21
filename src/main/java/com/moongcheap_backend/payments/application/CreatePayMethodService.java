package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient;
import com.moongcheap_backend.payments.infrastructure.BrandPayMethodClient.MethodsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** SDK 등록 완료 후 토스의 전체 결제수단을 조회해 로컬 DB 동기화를 시작한다. */
@Service
@RequiredArgsConstructor
public class CreatePayMethodService {

    private final BrandPayTokenService brandPayTokenService;
    private final BrandPayMethodClient brandPayMethodClient;
    private final BrandPayMethodSyncService syncService;

    /**
     * 외부 HTTP 호출은 DB 트랜잭션 밖에서 수행하고, 조회가 모두 성공한 경우에만
     * 별도 동기화 서비스의 짧은 트랜잭션으로 로컬 결제수단을 변경한다.
     */
    public void synchronize(Long memberId) {
        String accessToken = brandPayTokenService.getValidAccessToken(memberId);
        MethodsResponse response = brandPayMethodClient.getAll(accessToken);
        syncService.synchronize(memberId, response);
    }
}
