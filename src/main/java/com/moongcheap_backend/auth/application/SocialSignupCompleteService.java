package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.domain.NicknameValidator;
import com.moongcheap_backend.auth.presentation.dto.SocialSignupCompleteRequestDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialSignupCompleteService {

    private final MemberRepository memberRepository;
    private final NicknameService nicknameService;
    private final PrincipalFactory principalFactory;

    @Transactional
    public SessionPrincipal complete(Long memberId, SocialSignupCompleteRequestDto request) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.isTermsAgreed()) {
            throw new BusinessException(ErrorCode.SOCIAL_SIGNUP_ALREADY_COMPLETE);
        }

        if (request.nickname() != null && !request.nickname().isBlank()) {
            String key = NicknameValidator.toKey(NicknameValidator.normalize(request.nickname()));
            if (!key.equalsIgnoreCase(member.getNickname())) {
                nicknameService.ensureAvailable(key);
                member.changeProfile(key, null, null, null);
            }
        }

        member.agreeTerms();
        return principalFactory.build(member);
    }
}
