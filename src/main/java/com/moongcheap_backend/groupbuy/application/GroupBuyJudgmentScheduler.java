package com.moongcheap_backend.groupbuy.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyJudgmentScheduler {

    private static final int BATCH_SIZE = 100;
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final GroupBuyJudgmentSchedule judgmentSchedule;
    private final GroupBuyJudgmentService judgmentService;

    // Sorted Set은 작업을 자동 삭제하지 않으므로 완료된 판정만 명시적으로 제거한다.
    @Scheduled(fixedDelayString = "${moongcheap.group-buy.judgment-poll-delay-ms}")
    public void judgeDueGroupBuys() {
        Set<String> dueGroupBuyIds = judgmentSchedule.findDue(
            LocalDateTime.now(ZONE_SEOUL), BATCH_SIZE);

        for (String member : dueGroupBuyIds) {
            judge(member);
        }
    }

    private void judge(String member) {
        Long groupBuyId;
        try {
            groupBuyId = Long.valueOf(member);
        } catch (NumberFormatException exception) {
            log.warn("Removing invalid group-buy judgment member: member={}", member);
            judgmentSchedule.remove(member);
            return;
        }

        try {
            judgmentService.judgeAndPay(groupBuyId);
            judgmentSchedule.remove(groupBuyId);
        } catch (BusinessException exception) {
            // 다른 워커가 이미 판정했거나 삭제된 데이터는 재시도할 필요가 없다.
            if (exception.getErrorCode() == ErrorCode.GROUPBUY_NOT_FOUND) {
                log.info("Removing stale group-buy judgment: groupBuyId={}", groupBuyId);
                judgmentSchedule.remove(groupBuyId);
                return;
            }
            log.warn("Group-buy judgment failed: groupBuyId={}", groupBuyId, exception);
        } catch (RuntimeException exception) {
            // 일시적인 DB 장애 등은 member를 남겨 다음 스케줄에 다시 시도한다.
            log.warn("Group-buy judgment failed: groupBuyId={}", groupBuyId, exception);
        }
    }
}
