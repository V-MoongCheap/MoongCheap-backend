package com.moongcheap_backend.member.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.application.SellerPublicService;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerPublicServiceFailureTest {

    @Mock private SellerRepository sellerRepository;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private SellerPublicService service;

    @Nested
    @DisplayName("detail - 실패")
    class DetailTest {

        @Test
        void APPROVED_상태가_아니거나_존재하지_않는_판매자를_조회한다() {
            when(sellerRepository.findByIdAndStatusAndDeletedAtIsNull(1L, SellerStatus.APPROVED))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELLER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getByIdAndStatus - 실패")
    class GetByIdAndStatusTest {

        @Test
        void 존재하지_않는_판매자를_조회한다() {
            when(sellerRepository.findByIdAndStatusAndDeletedAtIsNull(1L, SellerStatus.PENDING))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByIdAndStatus(1L, SellerStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELLER_NOT_FOUND);
        }
    }
}
