package com.moongcheap_backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다."),

    // Auth
    LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "AUTH_001", "이미 사용 중인 아이디입니다."),
    LOGIN_ID_INVALID(HttpStatus.BAD_REQUEST, "AUTH_002", "아이디 형식이 올바르지 않습니다."),
    PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "AUTH_003", "비밀번호 형식이 올바르지 않습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_004", "비밀번호가 일치하지 않습니다."),
    PASSWORD_CONTAINS_LOGIN_ID(HttpStatus.BAD_REQUEST, "AUTH_005", "비밀번호에 아이디를 포함할 수 없습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_006", "아이디 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_LOCKED(HttpStatus.LOCKED, "AUTH_007", "연속 로그인 실패로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요."),
    LOCAL_CREDENTIAL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_008", "비밀번호가 등록된 회원만 사용할 수 있습니다."),
    PASSWORD_SAME_AS_PREVIOUS(HttpStatus.BAD_REQUEST, "AUTH_009", "이전과 동일한 비밀번호는 사용할 수 없습니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "AUTH_010", "OAuth 상태값 검증에 실패했습니다."),
    SOCIAL_ALREADY_LINKED(HttpStatus.CONFLICT, "AUTH_011", "이미 다른 회원에 연동된 소셜 계정입니다."),
    LAST_CREDENTIAL_CANNOT_UNLINK(HttpStatus.BAD_REQUEST, "AUTH_012", "마지막 로그인 수단은 해제할 수 없습니다."),
    SOCIAL_SIGNUP_ALREADY_COMPLETE(HttpStatus.BAD_REQUEST, "AUTH_013", "이미 소셜 가입이 완료되었습니다."),
    SOCIAL_SIGNUP_INCOMPLETE(HttpStatus.FORBIDDEN, "AUTH_016", "소셜 가입 완료가 필요합니다."),
    WITHDRAW_BLOCKED_HAS_ORDER(HttpStatus.BAD_REQUEST, "AUTH_014", "진행 중인 거래가 있어 탈퇴할 수 없습니다."),
    CONCURRENT_SIGNUP_CONFLICT(HttpStatus.CONFLICT, "AUTH_015", "회원가입에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "회원을 찾을 수 없습니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 닉네임입니다."),
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "USER_003", "닉네임 형식이 올바르지 않습니다."),

    // Shipping
    SHIPPING_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "SHIP_001", "배송지를 찾을 수 없습니다."),
    SHIPPING_ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "SHIP_002", "배송지는 최대 5개까지 등록할 수 있습니다."),
    SHIPPING_ADDRESS_FORBIDDEN(HttpStatus.FORBIDDEN, "SHIP_003", "본인 소유의 배송지가 아닙니다."),
    SHIPPING_ADDRESS_DEFAULT_CONFLICT(HttpStatus.CONFLICT, "SHIP_004",
        "기본 배송지 변경이 충돌했습니다. 다시 시도해주세요."),

    // 동시성
    CONCURRENT_REQUEST_CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "요청이 충돌했습니다. 잠시 후 다시 시도해주세요."),

    // Product
    PRODUCT_CATALOG_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품 카탈로그를 찾을 수 없습니다."),
    PRODUCT_NOT_ORDERABLE(HttpStatus.CONFLICT, "PRODUCT_002", "현재 주문할 수 없는 상품입니다."),

    // Demand
    DEMAND_ALREADY_EXISTS(HttpStatus.CONFLICT, "DEMAND_001", "이미 진행 중인 수요 요청이 있습니다."),
    DEMAND_NOT_FOUND(HttpStatus.NOT_FOUND, "DEMAND_002", "수요 요청을 찾을 수 없습니다."),
    DEMAND_FORBIDDEN(HttpStatus.FORBIDDEN, "DEMAND_003", "본인의 수요 요청이 아닙니다."),
    DEMAND_BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "DEMAND_004", "수요 보드를 찾을 수 없습니다."),
    DEMAND_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DEMAND_005", "현재 상태에서는 수요를 취소할 수 없습니다."),
    DEMAND_BOARD_CLOSED(HttpStatus.BAD_REQUEST, "DEMAND_006", "마감된 수요 보드입니다."),
    DEMAND_ACCEPT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DEMAND_007", "현재 상태에서는 대체 오퍼를 승낙할 수 없습니다."),
    DEMAND_ACCEPT_CATALOG_CONFLICT(HttpStatus.CONFLICT, "DEMAND_008", "제안된 상품과 동일한 상품의 진행 중인 수요가 이미 존재하여 승낙할 수 없습니다."),
    DEMAND_DESIRE_EXPIRED(HttpStatus.BAD_REQUEST, "DEMAND_009", "수요 희망 기간이 만료되었습니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "주문을 찾을 수 없습니다."),
    ORDER_CANNOT_CANCEL(HttpStatus.CONFLICT, "ORDER_002", "현재 상태에서는 주문을 취소할 수 없습니다."),
    ORDER_CANNOT_SHIPPING(HttpStatus.CONFLICT, "ORDER_003", "현재 상태에서는 배송지를 입력할 수 없습니다."),

    // Seller
    SELLER_ALREADY_REGISTERED(HttpStatus.CONFLICT, "SELLER_001", "이미 판매자로 등록되어 있습니다."),
    BUSINESS_NUMBER_INVALID(HttpStatus.BAD_REQUEST, "SELLER_002", "사업자등록번호 형식이 올바르지 않습니다."),
    BUSINESS_NUMBER_DUPLICATED(HttpStatus.CONFLICT, "SELLER_003", "이미 등록된 사업자등록번호입니다."),
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_004", "판매자 정보를 찾을 수 없습니다."),
    SELLER_MUTABLE_FIELD_ONLY(HttpStatus.BAD_REQUEST, "SELLER_007", "해당 필드는 수정할 수 없습니다."),
    SELLER_NOT_APPROVED(HttpStatus.FORBIDDEN, "SELLER_008", "승인된 판매자만 사용할 수 있습니다."),

    // GroupBuy
    GROUPBUY_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUPBUY_001", "공동구매를 찾을 수 없습니다."),
    GROUPBUY_NOT_OPEN(HttpStatus.CONFLICT, "GROUPBUY_002", "현재 주문할 수 없는 공동구매입니다."),

    // Search
    SEARCH_INDEX_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SEARCH_001", "상품 검색 인덱싱에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
