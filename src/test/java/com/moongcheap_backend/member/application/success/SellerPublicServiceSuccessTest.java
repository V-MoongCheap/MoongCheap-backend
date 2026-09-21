package com.moongcheap_backend.member.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.application.SellerPublicService;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.member.presentation.dto.SellerPublicResponseDto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerPublicServiceSuccessTest {

    @Mock private SellerRepository sellerRepository;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private SellerPublicService service;

    @Nested
    @DisplayName("detail - 성공")
    class DetailTest {

        @Test
        void APPROVED_판매자_공개_정보를_조회한다() {
            Long sellerId = 1L;
            Seller seller = mock(Seller.class);
            when(seller.getBusinessNumber()).thenReturn("encrypted");
            when(seller.getBusinessName()).thenReturn("문치프 스토어");
            when(seller.getOwnerName()).thenReturn("홍길동");
            when(seller.getMailOrderRegistrationNumber()).thenReturn("2024-서울강남-1234");
            when(seller.getPhoneNumber()).thenReturn("010-1234-5678");
            when(sellerRepository.findByIdAndStatusAndDeletedAtIsNull(sellerId, SellerStatus.APPROVED))
                .thenReturn(Optional.of(seller));
            when(encryptionService.decrypt("encrypted")).thenReturn("1234567890");
            when(encryptionService.maskBusinessNumber("1234567890"))
                .thenReturn("123-45-67***");

            SellerPublicResponseDto result = service.detail(sellerId);

            assertThat(result.businessNumberMasked()).isEqualTo("123-45-67***");
            assertThat(result.businessName()).isEqualTo("문치프 스토어");
        }
    }

    @Nested
    @DisplayName("getByIdAndStatus - 성공")
    class GetByIdAndStatusTest {

        @Test
        void 특정_status의_판매자를_조회한다() {
            Long sellerId = 1L;
            Seller seller = mock(Seller.class);
            when(sellerRepository.findByIdAndStatusAndDeletedAtIsNull(sellerId, SellerStatus.APPROVED))
                .thenReturn(Optional.of(seller));

            Seller result = service.getByIdAndStatus(sellerId, SellerStatus.APPROVED);

            assertThat(result).isSameAs(seller);
        }
    }
}
