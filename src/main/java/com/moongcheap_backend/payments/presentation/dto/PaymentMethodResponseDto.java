package com.moongcheap_backend.payments.presentation.dto;

import com.moongcheap_backend.payments.domain.enums.PaymentsMethodStatus;

public record PaymentMethodResponseDto(
    Long id,
    String provider, //은행, 카드사
    String number, //계좌, 카드번호
    Boolean isDefault, //기본결제수단 여부
    PaymentsMethodStatus status //결제수단상태
) {

}
