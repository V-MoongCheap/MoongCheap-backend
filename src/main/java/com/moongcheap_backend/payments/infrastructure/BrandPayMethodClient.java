package com.moongcheap_backend.payments.infrastructure;

import com.moongcheap_backend.payments.domain.enums.PaymentType;
import java.util.List;

/** 유효한 Access Token으로 토스페이먼츠에 등록된 전체 결제수단을 조회하는 규격이다. */
public interface BrandPayMethodClient {

    /**
     * Access Token과 연결된 고객의 활성 카드 및 계좌를 모두 조회한다.
     *
     * @param accessToken 백엔드 내부에서 확보한 유효한 브랜드페이 Access Token
     * @return 기본 선택 ID와 전체 카드·계좌 목록
     */
    MethodsResponse getAll(String accessToken);

    /**
     * 결제수단 유형에 맞는 토스 삭제 API를 멱등하게 호출한다.
     *
     * @param accessToken 유효한 브랜드페이 Access Token
     * @param methodKey 삭제할 토스 결제수단 식별키
     * @param type 카드 또는 계좌 유형
     * @param idempotencyKey 동일 삭제 요청을 식별하는 멱등키
     */
    void remove(String accessToken, String methodKey, PaymentType type,
        String idempotencyKey);

    record MethodsResponse(
        boolean isIdentified,
        String selectedMethodId,
        List<Card> cards,
        List<Account> accounts
    ) {
        public MethodsResponse {
            cards = cards == null ? List.of() : List.copyOf(cards);
            accounts = accounts == null ? List.of() : List.copyOf(accounts);
        }
    }

    record Card(
        String id,
        String methodKey,
        String cardNumber,
        String issuerCode,
        String status
    ) {
    }

    record Account(
        String id,
        String methodKey,
        String accountNumber,
        String bankCode,
        String status
    ) {
    }
}
