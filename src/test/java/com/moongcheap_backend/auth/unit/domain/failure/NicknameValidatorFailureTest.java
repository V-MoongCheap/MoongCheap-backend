package com.moongcheap_backend.auth.unit.domain.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.auth.domain.NicknameValidator;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NicknameValidatorFailureTest {

    @Nested
    @DisplayName("normalize - 실패")
    class NormalizeTest {

        @Test
        void null값인_닉네임을_입력한다() {
            assertThatThrownBy(() -> NicknameValidator.normalize(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_INVALID);
        }

        @Test
        void 경계값_1글자_닉네임을_입력한다() {
            assertThatThrownBy(() -> NicknameValidator.normalize("홍"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_INVALID);
        }

        @Test
        void 경계값_21글자_닉네임을_입력한다() {
            String input = "a".repeat(21);
            assertThatThrownBy(() -> NicknameValidator.normalize(input))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_INVALID);
        }

        @Test
        void 공백인_닉네임을_입력한다() {
            assertThatThrownBy(() -> NicknameValidator.normalize("   "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_INVALID);
        }
    }
}
