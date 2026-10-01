package com.moongcheap_backend.member.application;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.member.presentation.dto.OrderMemberInfoDto;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderMemberInfoService {

    private final MemberRepository memberRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public OrderMemberInfoDto getForOrder(Long memberId, Long shippingAddressId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        ShippingAddress address = shippingAddressRepository.findById(shippingAddressId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_ADDRESS_NOT_FOUND));
        if (!address.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_FORBIDDEN);
        }
        String buyerPhone = encryptionService.decrypt(member.getPhoneNumber());
        String shipPhone = encryptionService.decrypt(address.getPhoneNumber());
        return new OrderMemberInfoDto(
            member.getNickname(),
            buyerPhone,
            new OrderMemberInfoDto.ShippingSnapshot(
                address.getRecipientName(),
                shipPhone,
                address.getZipcode(),
                address.getAddress(),
                address.getAddressDetail(),
                address.getEntranceCode(),
                address.getRequestMessage()
            )
        );
    }

    //회원 상태 검증용 메서드
    @Transactional(readOnly = true)
    public void validateActiveMember(Long memberId) {
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> getActiveMemberIds(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Set.of();
        }
        return memberRepository.findActiveMemberIds(memberIds);
    }
}
