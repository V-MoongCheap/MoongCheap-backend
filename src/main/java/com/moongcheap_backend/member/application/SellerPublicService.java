package com.moongcheap_backend.member.application;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.member.presentation.dto.SellerPublicResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerPublicService {

    private final SellerRepository sellerRepository;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public SellerPublicResponseDto detail(Long sellerId) {
        Seller seller = getByIdAndStatus(sellerId, SellerStatus.APPROVED);
        String bizPlain = encryptionService.decrypt(seller.getBusinessNumber());
        return new SellerPublicResponseDto(
            seller.getBusinessName(),
            seller.getOwnerName(),
            encryptionService.maskBusinessNumber(bizPlain),
            seller.getMailOrderRegistrationNumber(),
            seller.getPhoneNumber()
        );
    }

    @Transactional(readOnly = true)
    public Seller getByIdAndStatus(Long sellerId, SellerStatus status) {
        return sellerRepository.findByIdAndStatusAndDeletedAtIsNull(sellerId, status)
            .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
    }
}
