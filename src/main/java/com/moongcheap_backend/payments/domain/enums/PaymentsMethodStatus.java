package com.moongcheap_backend.payments.domain.enums;

public enum PaymentsMethodStatus {
    ACTIVE, //사용 가능
    INACTIVE, //사용 불가
    EXPIRED //삭제된 결제수단
}
