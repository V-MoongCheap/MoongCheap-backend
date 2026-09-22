package com.moongcheap_backend.auth.unit.domain.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.auth.domain.PasswordValidator;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordValidatorFailureTest {

    @Nested
    @DisplayName("validate - 실패")
    class ValidateTest {

        @Test
        void null을_입력한다() {
            assertThatThrownBy(() -> PasswordValidator.validate(null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void 경계값_7자_이하_비밀번호를_입력한다() {
            assertThatThrownBy(() -> PasswordValidator.validate("pass123", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void 경계값_65자_이상_비밀번호를_입력한다() {
            String input = "a1".repeat(33);
            assertThatThrownBy(() -> PasswordValidator.validate(input, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void 영문만으로_구성된_비밀번호를_입력한다() {
            assertThatThrownBy(() -> PasswordValidator.validate("password", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void 숫자만으로_구성된_비밀번호를_입력한다() {
            assertThatThrownBy(() -> PasswordValidator.validate("12345678", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void 특수문자만으로_구성된_비밀번호를_입력한다() {
            assertThatThrownBy(() -> PasswordValidator.validate("!@#$%^&*", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_INVALID);
        }

        @Test
        void loginId를_포함한_비밀번호를_입력한다() {
            assertThatThrownBy(() -> PasswordValidator.validate("user12341234", "user1234"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_CONTAINS_LOGIN_ID);
        }
    }
}
