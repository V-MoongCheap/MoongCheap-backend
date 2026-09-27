package com.moongcheap_backend.groupbuy.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.payments.application.GroupPaymentReservationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyJudgmentScheduler {

    private static final int BATCH_SIZE = 100;
    // 정상 판정은 공동구매 마감 5분 후 실행한다.
    private static final long JUDGMENT_DELAY_MINUTES = 5;
    // 정상 backlog를 유실로 오인하지 않도록 판정 예정시각 이후 1시간을 더 기다린다.
    private static final long RECOVERY_GRACE_MINUTES = 60;
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final GroupBuyJudgmentSchedule judgmentSchedule;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyJudgmentService judgmentService;
    private final GroupPaymentReservationService paymentReservationService;

    // Sorted Set은 작업을 자동 삭제하지 않으므로 완료된 판정만 명시적으로 제거한다.
    @Scheduled(fixedDelayString = "${moongcheap.group-buy.judgment-poll-delay-ms}")
    public void judgeDueGroupBuys() {
        LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
        Set<String> dueGroupBuyIds = judgmentSchedule.findDue(now, BATCH_SIZE);
        dueGroupBuyIds.forEach(this::judge);

        // 정상 판정을 먼저 끝낸 뒤 별도로 누락 예약만 복원한다.
        recoverMissingSchedules(now);
    }

    private void recoverMissingSchedules(LocalDateTime now) {
        // 정상 예약의 처리 순서를 우회하지 않고, 실제로 유실된 member만 복원한다.
        for (GroupBuy groupBuy : groupBuyRepository.findDueForJudgmentRecovery(
                GroupBuyStatus.OPEN,
                now.minusMinutes(JUDGMENT_DELAY_MINUTES + RECOVERY_GRACE_MINUTES),
                PageRequest.of(0, BATCH_SIZE))) {
            if (judgmentSchedule.score(groupBuy.getId()) == null) {
                // 원래 score를 복원해 다음 폴링부터 기존 시간순 판정 경로를 타게 한다.
                LocalDateTime scheduledAt = groupBuy.getGroupBuyEndAt()
                    .plusMinutes(JUDGMENT_DELAY_MINUTES);
                judgmentSchedule.schedule(groupBuy.getId(), scheduledAt);
                log.warn("Recovered missing group-buy judgment schedule: groupBuyId={}",
                    groupBuy.getId());
            }
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
            boolean recruitmentCompleted = judgmentService.judgeAndPay(groupBuyId);
            if (recruitmentCompleted) {
                // judgeAndPay의 트랜잭션이 반환되며 커밋된 뒤 결제 예약을 생성한다.
                try {
                    paymentReservationService.scheduleForGroup(groupBuyId);
                } catch (RuntimeException exception) {
                    // 판정은 이미 커밋됐으므로 되돌리지 않는다. 복구 스케줄러가 보완한다.
                    log.warn("Payment reservation deferred after group-buy judgment: groupBuyId={}",
                        groupBuyId);
                }
            }
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
