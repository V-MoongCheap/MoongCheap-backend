package com.moongcheap_backend.payments.domain.enums;

public enum PaymentsStatus {
    PENDING,          // 자동결제 실행을 기다리는 최초 대기 상태
    UNKNOWN,          // 외부 요청이 시작됐거나 결과가 불명확하여 조회·재조정이 필요한 상태
    REVIEW_REQUIRED,  // 자동 복구 한도 초과 또는 응답 검증 실패로 수동 확인이 필요한 상태
    SUCCEEDED,        // 토스 결제 승인이 완료된 상태
    FAILED,           // 결제 거절 또는 실행 조건 불충족으로 실패가 확정된 상태
    CANCELED          // 승인된 결제의 전액 취소가 완료된 상태
}
