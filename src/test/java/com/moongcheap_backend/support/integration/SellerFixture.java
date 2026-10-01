package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SellerFixture {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private EncryptionService encryptionService;

    public Seller saveApproved(Long memberId, String businessName, String businessNumber) {
        Seller seller = Seller.builder()
            .memberId(memberId)
            .businessName(businessName)
            .businessNumber(encryptionService.encrypt(businessNumber))
            .businessNumberHash(hash(businessNumber))
            .mailOrderRegistrationNumber("2024-서울-1234")
            .ownerName("홍길동")
            .phoneNumber("02-1234-5678")
            .build();
        seller.approve();
        return sellerRepository.save(seller);
    }

    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
