package com.moongcheap_backend.auth.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequestDto(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(max = 100) String password,
        @NotBlank @Size(max = 100) String passwordConfirm,
        @NotBlank @Size(max = 20) String nickname,
        @Email String email,

        @AssertTrue(message = "이용 약관에 동의해야 합니다.")
        boolean termsAgreed,

        @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다")
        boolean policyAgreed,

        @AssertTrue(message = "만 14세 이상 동의해야 합니다.")
        boolean ageVerified
) {
}
