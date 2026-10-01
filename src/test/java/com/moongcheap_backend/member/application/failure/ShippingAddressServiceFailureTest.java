package com.moongcheap_backend.member.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.member.application.ShippingAddressService;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressEditRequestDto;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressRequestDto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingAddressServiceFailureTest {

    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private AdvisoryLockAdaptor advisoryLockAdaptor;

    @InjectMocks
    private ShippingAddressService service;

    private ShippingAddressRequestDto createRequest() {
        return new ShippingAddressRequestDto(
            "집", "홍길동", "010-1234-5678", "06235",
            "서울 강남구 테헤란로 427", "101동 202호",
            "1234#", "문 앞에 놓아주세요", false);
    }

    private ShippingAddressEditRequestDto editRequest() {
        return new ShippingAddressEditRequestDto(
            "회사", "홍길동", "010-1234-5678", "06235",
            "서울 강남구 테헤란로 427", "301동 1001호",
            null, "경비실");
    }

    @Nested
    @DisplayName("create - 실패")
    class CreateTest {

        @Test
        void 배송지가_이미_5개인_상태에서_추가한다() {
            Long memberId = 1L;
            when(shippingAddressRepository.countByMemberId(memberId)).thenReturn(5L);

            assertThatThrownBy(() -> service.create(memberId, createRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED);
        }

        @Test
        void 배송지가_5개_초과_상태에서_추가한다() {
            Long memberId = 1L;
            when(shippingAddressRepository.countByMemberId(memberId)).thenReturn(6L);

            assertThatThrownBy(() -> service.create(memberId, createRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("getById - 실패")
    class GetByIdTest {

        @Test
        void 존재하지_않는_배송지를_조회한다() {
            when(shippingAddressRepository.findById(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
        }

        @Test
        void 다른_회원_소유_배송지를_조회한다() {
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(99L);
            when(shippingAddressRepository.findById(100L)).thenReturn(Optional.of(address));

            assertThatThrownBy(() -> service.getById(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("edit - 실패")
    class EditTest {

        @Test
        void 다른_회원_소유_배송지를_수정한다() {
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(99L);
            when(shippingAddressRepository.findById(100L)).thenReturn(Optional.of(address));

            assertThatThrownBy(() -> service.edit(1L, 100L, editRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("delete - 실패")
    class DeleteTest {

        @Test
        void 삭제_쿼리가_실패한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(shippingAddressRepository.deleteByIdAndMemberId(addressId, memberId))
                .thenReturn(0);

            assertThatThrownBy(() -> service.delete(memberId, addressId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("markAsDefault - 실패")
    class MarkAsDefaultTest {

        @Test
        void setAsDefault_쿼리가_실패한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(address.isDefault()).thenReturn(false);
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(shippingAddressRepository.setAsDefault(addressId, memberId))
                .thenReturn(0);

            assertThatThrownBy(() -> service.markAsDefault(memberId, addressId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                    ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
        }
    }
}
