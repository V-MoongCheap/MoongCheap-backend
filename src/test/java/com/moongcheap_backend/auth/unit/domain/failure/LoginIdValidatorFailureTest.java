package com.moongcheap_backend.auth.unit.domain.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.auth.domain.LoginIdValidator;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoginIdValidatorFailureTest {

    @Nested
    @DisplayName("normalizeAndValidate - 실패")
    class NormalizeAndValidateTest {

        @Test
        void null을_입력한다() {
            assertThatThrownBy(() -> LoginIdValidator.normalizeAndValidate(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_ID_INVALID);
        }

        @Test
        void 경계값_3자리_이하_아이디를_입력한다() {
            assertThatThrownBy(() -> LoginIdValidator.normalizeAndValidate("abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_ID_INVALID);
        }

        @Test
        void 경계값_21자리_이상_아이디를_입력한다() {
            String input = "a" + "b".repeat(20);
            assertThatThrownBy(() -> LoginIdValidator.normalizeAndValidate(input))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_ID_INVALID);
        }

        @Test
        void 숫자로_시작하는_아이디를_입력한다() {
            assertThatThrownBy(() -> LoginIdValidator.normalizeAndValidate("1user234"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_ID_INVALID);
        }

        @Test
        void underscore_외_특수문자가_포함된_아이디를_입력한다() {
            assertThatThrownBy(() -> LoginIdValidator.normalizeAndValidate("user@123"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_ID_INVALID);
        }
    }
}
