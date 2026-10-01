package com.moongcheap_backend.payments.infrastructure;

public class PaymentGatewayException extends RuntimeException {
    public enum Kind { DECLINED, UNKNOWN, CONFIGURATION, INVALID_RESPONSE }
    private final Kind kind;
    private final String code;

    public PaymentGatewayException(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
}
