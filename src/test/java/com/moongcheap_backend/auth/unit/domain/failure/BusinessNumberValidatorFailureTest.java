package com.moongcheap_backend.auth.unit.domain.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.auth.domain.BusinessNumberValidator;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BusinessNumberValidatorFailureTest {

    @Nested
    @DisplayName("normalizeAndValidate - 실패")
    class NormalizeAndValidateTest {

        @Test
        void null값인_사업자_번호를_입력한다() {
            assertThatThrownBy(() -> BusinessNumberValidator.normalizeAndValidate(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_NUMBER_INVALID);
        }

        @Test
        void 숫자가_10자리_미만인_사업자_번호를_입력한다() {
            assertThatThrownBy(() -> BusinessNumberValidator.normalizeAndValidate("123456789"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_NUMBER_INVALID);
        }

        @Test
        void 숫자가_10자리_초과인_사업자_번호를_입력한다() {
            assertThatThrownBy(() -> BusinessNumberValidator.normalizeAndValidate("12345678901"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_NUMBER_INVALID);
        }

        @Test
        void 모두_같은_숫자로_반복된_유효하지_않은_사업자_번호를_입력한다() {
            assertThatThrownBy(() -> BusinessNumberValidator.normalizeAndValidate("1111111111"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_NUMBER_INVALID);
        }

        @Test
        void checksum이_불일치하는_유효하지_않은_사업자_번호를_입력한다() {
            // 123-45-67890: checksum 불일치
            assertThatThrownBy(() -> BusinessNumberValidator.normalizeAndValidate("123-45-67890"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_NUMBER_INVALID);
        }
    }
}
