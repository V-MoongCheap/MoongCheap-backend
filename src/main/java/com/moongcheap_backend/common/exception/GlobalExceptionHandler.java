package com.moongcheap_backend.common.exception;

import com.moongcheap_backend.common.response.ApiError;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_MAP = Map.of(
            "uq_shipping_address_default",       ErrorCode.SHIPPING_ADDRESS_DEFAULT_CONFLICT,
            "uq_brand_pay_method_default",       ErrorCode.CONCURRENT_REQUEST_CONFLICT,
            "uq_demand_member_catalog_active",   ErrorCode.DEMAND_ALREADY_EXISTS,
            "uq_product_catalog_name",           ErrorCode.PRODUCT_CATALOG_DUPLICATED,
            "uq_seller_business_number_hash",    ErrorCode.BUSINESS_NUMBER_DUPLICATED,
            "uq_seller_member_id",               ErrorCode.SELLER_ALREADY_REGISTERED,
            "PK_NOTIFICATION_OPT_OUT",           ErrorCode.CONCURRENT_REQUEST_CONFLICT
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e) {
        List<ApiError.FieldError> fieldErrors = e.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    int dot = path.lastIndexOf('.');
                    return new ApiError.FieldError(dot >= 0 ? path.substring(dot + 1) : path, cv.getMessage());
                })
                .toList();
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage(), fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingRequestParameter(
        MissingServletRequestParameterException e
    ) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
            .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException e) {
        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleLockFailure(PessimisticLockingFailureException e) {
        ErrorCode ec = ErrorCode.CONCURRENT_REQUEST_CONFLICT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    /** @Version으로 감지한 오래된 저장 요청은 클라이언트가 재시도할 수 있는 충돌로 응답한다. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
        ObjectOptimisticLockingFailureException e
    ) {
        ErrorCode ec = ErrorCode.CONCURRENT_REQUEST_CONFLICT;
        return ResponseEntity.status(ec.getStatus())
            .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        if (msg != null) {
            for (Map.Entry<String, ErrorCode> entry : CONSTRAINT_ERROR_MAP.entrySet()) {
                if (msg.contains(entry.getKey())) {
                    ErrorCode ec = entry.getValue();
                    return ResponseEntity.status(ec.getStatus())
                            .body(ApiError.of(ec.getCode(), ec.getMessage()));
                }
            }
        }
        log.error("Unhandled data integrity violation", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiError.of(ec.getCode(), ec.getMessage()));
    }
}
