package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShippingAddressFixture {

    @Autowired
    private ShippingAddressRepository shippingAddressRepository;

    @Autowired
    private EncryptionService encryptionService;

    public ShippingAddress save(Long memberId, String alias, boolean isDefault) {
        return shippingAddressRepository.save(ShippingAddress.builder()
            .memberId(memberId)
            .alias(alias)
            .recipientName("홍길동")
            .phoneNumber(encryptionService.encrypt("01012345678"))
            .zipcode("06235")
            .address("서울특별시 강남구 테헤란로 427")
            .addressDetail("101동")
            .entranceCode("1234#")
            .requestMessage("문 앞에")
            .isDefault(isDefault)
            .build());
    }

    public void clear() {
        shippingAddressRepository.deleteAllInBatch();
    }
}
