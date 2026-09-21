package com.moongcheap_backend.payments.infrastructure;

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
