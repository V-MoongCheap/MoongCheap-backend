package com.moongcheap_backend.groupbuy.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//공동구매 판정 클래스
@Service
@RequiredArgsConstructor
public class GroupBuyJudgmentService {

    private final GroupBuyRepository groupBuyRepository;

    // 판정과 상태 변경을 커밋한 뒤에만 호출자가 Redis 예약을 제거할 수 있다.
    @Transactional
    public boolean judgeAndPay(Long groupBuyId) {
        boolean targetReached = judge(groupBuyId);

        if (targetReached) {
            // 결제 예약은 이 트랜잭션이 커밋된 뒤 GroupBuyJudgmentScheduler가 연결한다.
        }
        return targetReached;
    }

    // 저장소가 OPEN이면서 판정 시각이 지난 행만 반환하므로 결과는 성공/실패 둘 중 하나다.
    private boolean judge(Long groupBuyId) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuy groupBuy = groupBuyRepository.findExpiredByIdForJudgment(
                groupBuyId, GroupBuyStatus.OPEN, now)
            .orElseThrow(() -> new BusinessException(ErrorCode.GROUPBUY_NOT_FOUND));

        if (groupBuy.getCount() >= groupBuy.getTargetCount()) {
            groupBuy.completeRecruitment();
            return true;
        }

        groupBuy.fail();
        return false;
    }
}
