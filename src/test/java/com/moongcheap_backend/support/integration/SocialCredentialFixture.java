package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.member.domain.SocialCredential;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SocialCredentialFixture {

    @Autowired
    private SocialCredentialRepository socialCredentialRepository;

    public SocialCredential save(Long memberId, SocialProvider provider, String providerId) {
        return socialCredentialRepository.save(SocialCredential.builder()
            .memberId(memberId)
            .provider(provider)
            .providerId(providerId)
            .build());
    }
}
