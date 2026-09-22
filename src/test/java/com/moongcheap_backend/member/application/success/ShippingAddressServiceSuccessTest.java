package com.moongcheap_backend.member.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.member.application.ShippingAddressService;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressEditRequestDto;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressRequestDto;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressResponseDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingAddressServiceSuccessTest {

    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private AdvisoryLockAdaptor advisoryLockAdaptor;

    @InjectMocks
    private ShippingAddressService service;

    private ShippingAddressRequestDto createRequest(boolean setAsDefault) {
        return new ShippingAddressRequestDto(
            "집", "홍길동", "010-1234-5678", "06235",
            "서울 강남구 테헤란로 427", "101동 202호",
            "1234#", "문 앞에 놓아주세요", setAsDefault);
    }

    private ShippingAddressEditRequestDto editRequest() {
        return new ShippingAddressEditRequestDto(
            "회사", "홍길동", "010-1234-5678", "06235",
            "서울 강남구 테헤란로 427", "301동 1001호",
            null, "경비실");
    }

    @Nested
    @DisplayName("getAll - 성공")
    class GetAllTest {

        @Test
        void 회원의_배송지_목록을_조회한다() {
            Long memberId = 1L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getPhoneNumber()).thenReturn("encrypted");
            when(shippingAddressRepository
                .findAllByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId))
                .thenReturn(List.of(address));
            when(encryptionService.decrypt("encrypted")).thenReturn("01012345678");
            when(encryptionService.maskPhoneNumber("01012345678"))
                .thenReturn("010-****-5678");

            List<ShippingAddressResponseDto> result = service.getAll(memberId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).phoneNumberMasked()).isEqualTo("010-****-5678");
        }
    }

    @Nested
    @DisplayName("getById - 성공")
    class GetByIdTest {

        @Test
        void 본인_소유_배송지를_단건_조회한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(address.getPhoneNumber()).thenReturn("encrypted");
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(encryptionService.decrypt("encrypted")).thenReturn("01012345678");

            ShippingAddressResponseDto result = service.getById(memberId, addressId);

            assertThat(result.phoneNumberMasked()).isEqualTo("01012345678");
            verify(encryptionService, never()).maskPhoneNumber(anyString());
        }
    }

    @Nested
    @DisplayName("create - 성공")
    class CreateTest {

        @Test
        void 첫_번째_배송지를_등록한다() {
            Long memberId = 1L;
            when(shippingAddressRepository.countByMemberId(memberId)).thenReturn(0L);
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted");
            ShippingAddress saved = mock(ShippingAddress.class);
            when(saved.getId()).thenReturn(100L);
            when(shippingAddressRepository.save(any(ShippingAddress.class))).thenReturn(saved);

            Long result = service.create(memberId, createRequest(false));

            assertThat(result).isEqualTo(100L);
            verify(advisoryLockAdaptor).acquireXactLock(anyString(), anyString());
            verify(shippingAddressRepository).unmarkAllDefaults(memberId);
            ArgumentCaptor<ShippingAddress> captor = ArgumentCaptor.forClass(ShippingAddress.class);
            verify(shippingAddressRepository).save(captor.capture());
            assertThat(captor.getValue().isDefault()).isTrue();
        }

        @Test
        void 기존_주소가_있는_상태에서_기본_배송지로_등록한다() {
            Long memberId = 1L;
            when(shippingAddressRepository.countByMemberId(memberId)).thenReturn(1L);
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted");
            ShippingAddress saved = mock(ShippingAddress.class);
            when(saved.getId()).thenReturn(100L);
            when(shippingAddressRepository.save(any(ShippingAddress.class))).thenReturn(saved);

            service.create(memberId, createRequest(true));

            verify(shippingAddressRepository).unmarkAllDefaults(memberId);
            ArgumentCaptor<ShippingAddress> captor = ArgumentCaptor.forClass(ShippingAddress.class);
            verify(shippingAddressRepository).save(captor.capture());
            assertThat(captor.getValue().isDefault()).isTrue();
        }

        @Test
        void 기존_주소가_있는_상태에서_기본이_아닌_주소로_등록한다() {
            Long memberId = 1L;
            when(shippingAddressRepository.countByMemberId(memberId)).thenReturn(1L);
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted");
            ShippingAddress saved = mock(ShippingAddress.class);
            when(saved.getId()).thenReturn(100L);
            when(shippingAddressRepository.save(any(ShippingAddress.class))).thenReturn(saved);

            service.create(memberId, createRequest(false));

            verify(shippingAddressRepository, never()).unmarkAllDefaults(memberId);
            ArgumentCaptor<ShippingAddress> captor = ArgumentCaptor.forClass(ShippingAddress.class);
            verify(shippingAddressRepository).save(captor.capture());
            assertThat(captor.getValue().isDefault()).isFalse();
        }

        @Test
        void 배송지_4개인_상태에서_5번째_배송지를_등록한다() {
            Long memberId = 1L;
            when(shippingAddressRepository.countByMemberId(memberId)).thenReturn(4L);
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted");
            ShippingAddress saved = mock(ShippingAddress.class);
            when(saved.getId()).thenReturn(100L);
            when(shippingAddressRepository.save(any(ShippingAddress.class))).thenReturn(saved);

            Long result = service.create(memberId, createRequest(false));

            assertThat(result).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("edit - 성공")
    class EditTest {

        @Test
        void 본인_소유_배송지를_수정한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(encryptionService.encrypt("01012345678")).thenReturn("encrypted");

            service.edit(memberId, addressId, editRequest());

            verify(encryptionService).encrypt("01012345678");
            verify(address).update(
                eq("회사"), eq("홍길동"), eq("encrypted"), eq("06235"),
                eq("서울 강남구 테헤란로 427"), eq("301동 1001호"), eq(null), eq("경비실"));
        }
    }

    @Nested
    @DisplayName("delete - 성공")
    class DeleteTest {

        @Test
        void 본인_소유_배송지를_삭제한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(shippingAddressRepository.deleteByIdAndMemberId(addressId, memberId))
                .thenReturn(1);

            service.delete(memberId, addressId);

            verify(shippingAddressRepository).deleteByIdAndMemberId(addressId, memberId);
            verify(shippingAddressRepository).promoteOldestIfNoDefault(memberId);
        }
    }

    @Nested
    @DisplayName("markAsDefault - 성공")
    class MarkAsDefaultTest {

        @Test
        void 이미_기본_배송지인_주소를_기본으로_설정한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(address.isDefault()).thenReturn(true);
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));

            service.markAsDefault(memberId, addressId);

            verify(shippingAddressRepository, never()).unmarkDefaultExcept(any(), any());
            verify(shippingAddressRepository, never()).setAsDefault(any(), any());
        }

        @Test
        void 기본_배송지가_아닌_주소를_기본으로_설정한다() {
            Long memberId = 1L;
            Long addressId = 100L;
            ShippingAddress address = mock(ShippingAddress.class);
            when(address.getMemberId()).thenReturn(memberId);
            when(address.isDefault()).thenReturn(false);
            when(shippingAddressRepository.findById(addressId))
                .thenReturn(Optional.of(address));
            when(shippingAddressRepository.setAsDefault(addressId, memberId)).thenReturn(1);

            service.markAsDefault(memberId, addressId);

            verify(shippingAddressRepository).unmarkDefaultExcept(memberId, addressId);
            verify(shippingAddressRepository).setAsDefault(addressId, memberId);
        }
    }
}
