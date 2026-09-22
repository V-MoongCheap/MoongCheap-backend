package com.moongcheap_backend.auth.unit.domain.success;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.auth.domain.BusinessNumberValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BusinessNumberValidatorSuccessTest {

    @Nested
    @DisplayName("normalizeAndValidate - 성공")
    class NormalizeAndValidateTest {

        @Test
        void 유효한_사업자_번호를_입력한다() {
            String result = BusinessNumberValidator.normalizeAndValidate("1208147521");

            assertThat(result).isEqualTo("1208147521");
        }

        @Test
        void 하이픈이_포함된_유효한_사업자_번호를_입력한다() {
            String result = BusinessNumberValidator.normalizeAndValidate("120-81-47521");

            assertThat(result).isEqualTo("1208147521");
        }

        @Test
        void 공백이_포함된_유효한_사업자_번호를_입력한다() {
            String result = BusinessNumberValidator.normalizeAndValidate("120 81 47521");

            assertThat(result).isEqualTo("1208147521");
        }
    }
}
