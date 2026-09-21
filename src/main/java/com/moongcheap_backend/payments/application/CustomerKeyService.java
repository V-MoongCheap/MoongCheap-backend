package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.payments.domain.CustomerKey;
import com.moongcheap_backend.payments.presentation.dto.GetCustomerKeyResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerKeyService {

    private static final int KEY_CREATE_MAX_ATTEMPTS = 5;
    private static final String CUSTOMER_KEY_UNIQUE_CONSTRAINT = "uq_customer_key";

    private final CustomerKeyIssueTransactionService issueTransactionService;

    public CustomerKey createCustomerKey(Long memberId) {
        for (int attempt = 1; attempt <= KEY_CREATE_MAX_ATTEMPTS; attempt++) {
            try {
                return issueTransactionService.getOrCreate(memberId);
            } catch (DataIntegrityViolationException exception) {
                if (!isCustomerKeyCollision(exception)) {
                    throw exception;
                }

                if (attempt == KEY_CREATE_MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.CUSTOMER_KEY_ISSUE_FAILED);
                }
            }
        }

        throw new BusinessException(ErrorCode.CUSTOMER_KEY_ISSUE_FAILED);
    }

    public GetCustomerKeyResponse getCustomerKey(SessionPrincipal principal) {
        CustomerKey customerKey = createCustomerKey(principal.memberId());

        return new GetCustomerKeyResponse(customerKey.getCustomerKey());
    }

    private boolean isCustomerKeyCollision(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return CUSTOMER_KEY_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                    constraintViolation.getConstraintName()
                );
            }
            cause = cause.getCause();
        }
        return false;
    }
}
