package com.moongcheap_backend.auth.unit.domain.success;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.auth.domain.LoginIdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoginIdValidatorSuccessTest {

    @Nested
    @DisplayName("normalizeAndValidate - 성공")
    class NormalizeAndValidateTest {

        @Test
        void 유효한_소문자_아이디를_입력한다() {
            String result = LoginIdValidator.normalizeAndValidate("user1234");

            assertThat(result).isEqualTo("user1234");
        }

        @Test
        void 대문자가_포함된_유효한_아이디를_입력한다() {
            String result = LoginIdValidator.normalizeAndValidate("User1234");

            assertThat(result).isEqualTo("user1234");
        }

        @Test
        void 앞뒤_공백이_있는_유효한_아이디를_입력한다() {
            String result = LoginIdValidator.normalizeAndValidate("  user1234  ");

            assertThat(result).isEqualTo("user1234");
        }

        @Test
        void 경계값_최소_4자리_아이디를_입력한다() {
            // ^[a-z][a-z0-9_]{3,19}$ → 1 + 3 = 4자
            String result = LoginIdValidator.normalizeAndValidate("abcd");

            assertThat(result).isEqualTo("abcd");
        }

        @Test
        void 경계값_최대_20자리_아이디를_입력한다() {
            // 1 + 19 = 20자
            String input = "a" + "b".repeat(19);
            String result = LoginIdValidator.normalizeAndValidate(input);

            assertThat(result).isEqualTo(input);
        }
    }
}
