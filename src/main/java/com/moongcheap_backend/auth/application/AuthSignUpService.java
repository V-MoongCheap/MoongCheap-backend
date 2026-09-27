package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.presentation.dto.LoginIdAvailabilityResponseDto;
import com.moongcheap_backend.auth.presentation.dto.SignUpRequestDto;
import com.moongcheap_backend.auth.domain.LoginIdValidator;
import com.moongcheap_backend.auth.domain.NicknameValidator;
import com.moongcheap_backend.auth.domain.PasswordValidator;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSignUpService {

    private final MemberRepository memberRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public LoginIdAvailabilityResponseDto checkLoginId(String loginId) {
        String normalized = LoginIdValidator.normalizeAndValidate(loginId);
        boolean taken = memberRepository.existsByLoginIdAndDeletedAtIsNull(normalized);
        return new LoginIdAvailabilityResponseDto(normalized, !taken);
    }

    @Transactional
    public Long signUp(SignUpRequestDto request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        String loginId = LoginIdValidator.normalizeAndValidate(request.loginId());
        PasswordValidator.validate(request.password(), loginId);
        String nickname = NicknameValidator.normalize(request.nickname());
        if (memberRepository.existsByLoginIdAndDeletedAtIsNull(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATED);
        }
        Member newMember = Member.builder()
                .loginId(loginId)
                .nickname(NicknameValidator.toKey(nickname))
                .email(request.email())
                .build();
        newMember.agreeTerms();
        Member member;
        try {
            member = memberRepository.save(newMember);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONCURRENT_SIGNUP_CONFLICT);
        }
        localCredentialRepository.save(LocalCredential.builder()
                .memberId(member.getId())
                .password(passwordEncoder.encode(request.password()))
                .build());
        return member.getId();
    }
}
