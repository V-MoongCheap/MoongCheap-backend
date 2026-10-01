package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MemberFixture {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EncryptionService encryptionService;

    public Member save(String nickname) {
        return memberRepository.save(Member.builder()
            .loginId(null)
            .nickname(nickname)
            .email(nickname + "@test.com")
            .phoneNumber(null)
            .build());
    }

    public Member savePlainPhone(String nickname, String phoneNumber) {
        Member m = memberRepository.save(Member.builder()
            .loginId(null)
            .nickname(nickname)
            .email(nickname + "@test.com")
            .phoneNumber(encryptionService.encrypt(phoneNumber.replaceAll("[^0-9]", "")))
            .build());
        return m;
    }

    public Member saveSeller(String nickname) {
        Member m = save(nickname);
        m.becomeSeller();
        return memberRepository.save(m);
    }

    public Member saveWithTerms(String nickname, boolean termsAgreed) {
        Member m = save(nickname);
        if (termsAgreed) {
            m.agreeTerms();
            memberRepository.save(m);
        }
        return m;
    }

    public Member save() {
        return save("nick-" + System.nanoTime());
    }
}
