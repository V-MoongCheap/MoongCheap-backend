package com.moongcheap_backend.auth.unit.domain.success;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.moongcheap_backend.auth.domain.PasswordValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordValidatorSuccessTest {

    @Nested
    @DisplayName("validate - 성공")
    class ValidateTest {

        @Test
        void 영문과_숫자로_구성된_8자_이상_비밀번호를_입력한다() {
            assertThatCode(() -> PasswordValidator.validate("pass1234", "user1234"))
                .doesNotThrowAnyException();
        }

        @Test
        void loginId가_null인_상태에서_유효한_비밀번호를_입력한다() {
            assertThatCode(() -> PasswordValidator.validate("pass1234", null))
                .doesNotThrowAnyException();
        }
    }
}
