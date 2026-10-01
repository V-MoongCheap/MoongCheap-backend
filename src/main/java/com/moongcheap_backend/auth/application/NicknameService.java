package com.moongcheap_backend.auth.application;

import com.moongcheap_backend.auth.domain.NicknameValidator;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NicknameService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public boolean isAvailable(String nickname) {
        String key = NicknameValidator.toKey(NicknameValidator.normalize(nickname));
        return !memberRepository.existsByNicknameAndDeletedAtIsNull(key);
    }

    @Transactional(readOnly = true)
    public void ensureAvailable(String key) {
        if (memberRepository.existsByNicknameAndDeletedAtIsNull(key)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    @Transactional(readOnly = true)
    public String allocateForSocial(String candidate) {
        String base = NicknameValidator.toKey(NicknameValidator.normalize(
            candidate == null || candidate.isBlank() ? "user" : candidate));

        List<String> candidates = new ArrayList<>(10);
        while (candidates.size() < 10) {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 10000000);
            String name = truncate(base, 20 - String.valueOf(suffix).length()) + suffix;
            if (!candidates.contains(name)) {
                candidates.add(name);
            }
        }

        Set<String> taken = memberRepository.findTakenNicknames(candidates);
        return candidates.stream()
            .filter(name -> !taken.contains(name))
            .findFirst()
            .orElseThrow(
                () -> new BusinessException(ErrorCode.NICKNAME_DUPLICATED, "닉네임 자동 발급에 실패했습니다."));
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, Math.max(0, max)) : s;
    }
}
