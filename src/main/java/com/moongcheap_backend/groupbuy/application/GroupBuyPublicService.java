package com.moongcheap_backend.groupbuy.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupBuyPublicService {

    private final GroupBuyRepository groupBuyRepository;

    @Transactional
    public GroupBuy getOrderSource(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithSellerAndProductForUpdate(groupBuyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUPBUY_NOT_FOUND));

        if (groupBuy.getStatus() != GroupBuyStatus.OPEN) {
            throw new BusinessException(ErrorCode.GROUPBUY_NOT_OPEN);
        }

        return groupBuy;
    }

    @Transactional
    public void decreaseParticipantCount(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdForParticipantCountUpdate(groupBuyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUPBUY_NOT_FOUND));

        groupBuy.decreaseParticipantCount();
    }
}
