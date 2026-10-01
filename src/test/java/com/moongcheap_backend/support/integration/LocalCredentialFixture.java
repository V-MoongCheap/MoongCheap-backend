package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.member.domain.LocalCredential;
import com.moongcheap_backend.member.infrastructure.LocalCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LocalCredentialFixture {

    @Autowired
    private LocalCredentialRepository localCredentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public LocalCredential saveWithPassword(Long memberId, String rawPassword) {
        return localCredentialRepository.save(LocalCredential.builder()
            .memberId(memberId)
            .password(passwordEncoder.encode(rawPassword))
            .build());
    }
}
