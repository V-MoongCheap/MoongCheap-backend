package com.moongcheap_backend.auth.unit.domain.success;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.auth.domain.NicknameValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NicknameValidatorSuccessTest {

    @Nested
    @DisplayName("normalize - 성공")
    class NormalizeTest {

        @Test
        void 띄어쓰기가_없는_닉네임을_입력한다() {
            String result = NicknameValidator.normalize("홍길동");

            assertThat(result).isEqualTo("홍길동");
        }

        @Test
        void 띄어쓰기가_없는_2글자_닉네임을_입력한다() {
            String result = NicknameValidator.normalize("홍길");

            assertThat(result).isEqualTo("홍길");
        }

        @Test
        void 경계값_최대_20글자_닉네임을_입력한다() {
            String input = "a".repeat(20);
            String result = NicknameValidator.normalize(input);

            assertThat(result).isEqualTo(input);
        }

        @Test
        void 앞뒤에_공백이_있는_닉네임을_입력한다() {
            String result = NicknameValidator.normalize("  홍길동  ");

            assertThat(result).isEqualTo("홍길동");
        }

        @Test
        void 중간에_연속_공백이_있는_닉네임을_입력한다() {
            String result = NicknameValidator.normalize("홍  길  동");

            assertThat(result).isEqualTo("홍 길 동");
        }
    }
}
