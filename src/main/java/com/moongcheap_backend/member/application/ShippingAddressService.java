package com.moongcheap_backend.member.application;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.lock.AdvisoryLockAdaptor;
import com.moongcheap_backend.common.lock.AdvisoryLockKeys;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressEditRequestDto;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressRequestDto;
import com.moongcheap_backend.member.presentation.dto.ShippingAddressResponseDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private static final int MAX_ADDRESSES = 5;

    private static final String SHIPPING_LOCK_TIMEOUT = "3s";

    private final ShippingAddressRepository shippingAddressRepository;
    private final EncryptionService encryptionService;
    private final AdvisoryLockAdaptor advisoryLockAdaptor;

    @Transactional(readOnly = true)
    public List<ShippingAddressResponseDto> getAll(Long memberId) {
        return shippingAddressRepository
            .findAllByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId)
            .stream()
            .map(addr -> toResponse(addr, true))
            .toList();
    }

    @Transactional(readOnly = true)
    public ShippingAddressResponseDto getById(Long memberId, Long addressId) {
        return toResponse(loadOwned(memberId, addressId), false);
    }

    // 배송지 저장 제한 : 5개 — count 기반 제약은 SQL WHERE로 phantom read 방지 불가하여 Advisory Lock 사용
    @Transactional
    public Long create(Long memberId, ShippingAddressRequestDto request) {
        advisoryLockAdaptor.acquireXactLock(AdvisoryLockKeys.shippingAddressCreate(memberId),
            SHIPPING_LOCK_TIMEOUT);
        long existing = shippingAddressRepository.countByMemberId(memberId);
        if (existing >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_LIMIT_EXCEEDED);
        }
        boolean shouldBeDefault = existing == 0 || request.setAsDefault();
        if (shouldBeDefault) {
            shippingAddressRepository.unmarkAllDefaults(memberId);
        }
        String phoneDigits = request.phoneNumber().replaceAll("[^0-9]", "");
        ShippingAddress saved = shippingAddressRepository.save(ShippingAddress.builder()
            .memberId(memberId)
            .alias(request.alias())
            .recipientName(request.recipientName())
            .phoneNumber(encryptionService.encrypt(phoneDigits))
            .zipcode(request.zipcode())
            .address(request.address())
            .addressDetail(request.addressDetail())
            .entranceCode(request.entranceCode())
            .requestMessage(request.requestMessage())
            .isDefault(shouldBeDefault)
            .build());
        return saved.getId();
    }

    @Transactional
    public void edit(Long memberId, Long addressId, ShippingAddressEditRequestDto request) {
        ShippingAddress address = loadOwned(memberId, addressId);
        String phoneDigits = request.phoneNumber().replaceAll("[^0-9]", "");
        address.update(
            request.alias(),
            request.recipientName(),
            encryptionService.encrypt(phoneDigits),
            request.zipcode(),
            request.address(),
            request.addressDetail(),
            request.entranceCode(),
            request.requestMessage()
        );
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        loadOwned(memberId, addressId);
        int deleted = shippingAddressRepository.deleteByIdAndMemberId(addressId, memberId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
        }
        shippingAddressRepository.promoteOldestIfNoDefault(memberId);
    }

    @Transactional
    public void markAsDefault(Long memberId, Long addressId) {
        ShippingAddress target = loadOwned(memberId, addressId);
        if (target.isDefault()) {
            return;
        }
        shippingAddressRepository.unmarkDefaultExcept(memberId, addressId);
        int updated = shippingAddressRepository.setAsDefault(addressId, memberId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND);
        }
    }

    private ShippingAddress loadOwned(Long memberId, Long addressId) {
        ShippingAddress address = shippingAddressRepository.findById(addressId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND));
        if (!address.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }
        return address;
    }

    private ShippingAddressResponseDto toResponse(ShippingAddress a, Boolean applyPhoneMasking) {
        String phone = encryptionService.decrypt(a.getPhoneNumber());
        return new ShippingAddressResponseDto(
            a.getId(),
            a.getAlias(),
            a.getRecipientName(),
            applyPhoneMasking ?
                encryptionService.maskPhoneNumber(phone)
                : phone,
            a.getZipcode(),
            a.getAddress(),
            a.getAddressDetail(),
            a.getEntranceCode(),
            a.getRequestMessage(),
            a.isDefault()
        );
    }
}
