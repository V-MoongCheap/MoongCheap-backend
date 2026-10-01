package com.moongcheap_backend.payout.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.payout.domain.SellerKey;
import com.moongcheap_backend.payout.infrastructure.SellerKeyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerKeyService {

    private static final int SELLER_KEY_LENGTH = 20;
    private static final int KEY_CREATE_MAX_ATTEMPTS = 5;

    private final SellerKeyRepository sellerKeyRepository;

    public void createSellerKey(Seller seller) {
        if (sellerKeyRepository.existsById(seller.getId())) {
            return;
        }

        for (int attempt = 1; attempt <= KEY_CREATE_MAX_ATTEMPTS; attempt++) {
            SellerKey sellerKey = new SellerKey(seller, generateSellerKey());

            try {
                sellerKeyRepository.saveAndFlush(sellerKey);
                return;
            } catch (DataIntegrityViolationException exception) {
                if (sellerKeyRepository.existsById(seller.getId())) {
                    return;
                }

                if (attempt == KEY_CREATE_MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.SELLER_KEY_ISSUE_FAILED);
                }
            }
        }
    }

    private String generateSellerKey() {
        return UUID.randomUUID().toString().replace("-", "")
            .substring(0, SELLER_KEY_LENGTH);
    }
}
