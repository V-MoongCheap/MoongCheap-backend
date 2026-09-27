package com.moongcheap_backend.groupbuy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.payments.application.GroupPaymentReservationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * 테스트 대상: {@link GroupBuyJudgmentScheduler}의 Redis 만료 대상 처리 및 복구 기능
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyJudgmentSchedulerUnitTest {

    @Mock
    private GroupBuyJudgmentSchedule judgmentSchedule;

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyJudgmentService judgmentService;

    @Mock
    private GroupPaymentReservationService paymentReservationService;

    @InjectMocks
    private GroupBuyJudgmentScheduler scheduler;

    @Test
    @DisplayName("해피 케이스 - 판정 성공 후 Sorted Set에서 제거")
    void 판정에_성공한_공동구매는_Sorted_Set에서_제거한다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of("1"));
        when(judgmentService.judgeAndPay(1L)).thenReturn(true);

        scheduler.judgeDueGroupBuys();

        verify(judgmentService).judgeAndPay(1L);
        verify(paymentReservationService).scheduleForGroup(1L);
        verify(judgmentSchedule).remove(1L);
    }

    @Test
    void 모집실패_판정은_결제를_예약하지_않는다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of("1"));
        when(judgmentService.judgeAndPay(1L)).thenReturn(false);

        scheduler.judgeDueGroupBuys();

        verifyNoInteractions(paymentReservationService);
        verify(judgmentSchedule).remove(1L);
    }

    @Test
    @DisplayName("예외 케이스 - DB 장애 시 Sorted Set에 남겨 재시도")
    void 판정에_실패한_공동구매는_재시도를_위해_Sorted_Set에_남긴다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of("1"));
        doThrow(new IllegalStateException("DB unavailable"))
            .when(judgmentService).judgeAndPay(1L);

        scheduler.judgeDueGroupBuys();

        verify(judgmentSchedule, never()).remove(1L);
    }

    @Test
    @DisplayName("예외 케이스 - 이미 처리된 stale 예약 제거")
    void 이미_판정됐거나_삭제된_공동구매는_Sorted_Set에서_제거한다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of("1"));
        doThrow(new BusinessException(ErrorCode.GROUPBUY_NOT_FOUND))
            .when(judgmentService).judgeAndPay(1L);

        scheduler.judgeDueGroupBuys();

        verify(judgmentSchedule).remove(1L);
    }

    @Test
    @DisplayName("예외 케이스 - 재시도 가능한 비즈니스 예외 발생")
    void 재시도_가능한_비즈니스_예외가_발생하면_Sorted_Set에_남긴다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of("1"));
        doThrow(new BusinessException(ErrorCode.GROUPBUY_NOT_OPEN))
            .when(judgmentService).judgeAndPay(1L);

        scheduler.judgeDueGroupBuys();

        verify(judgmentSchedule, never()).remove(1L);
    }

    @Test
    @DisplayName("예외 케이스 - 숫자가 아닌 Redis member 제거")
    void 숫자가_아닌_member는_판정하지_않고_Sorted_Set에서_제거한다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of("invalid"));

        scheduler.judgeDueGroupBuys();

        verifyNoInteractions(judgmentService);
        verify(judgmentSchedule).remove("invalid");
    }

    @Test
    @DisplayName("해피 케이스 - 판정 대상이 없는 빈 배치")
    void 판정_대상이_없으면_판정_서비스를_호출하지_않는다() {
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of());

        scheduler.judgeDueGroupBuys();

        verifyNoInteractions(judgmentService);
        verify(judgmentSchedule, never()).remove(anyString());
    }

    @Test
    @DisplayName("복구 케이스 - Redis에서 유실된 예약만 Sorted Set에 복원")
    void Redis_예약이_유실된_공동구매는_Sorted_Set에_복원한다() {
        ZoneId zoneSeoul = ZoneId.of("Asia/Seoul");
        LocalDateTime before = LocalDateTime.now(zoneSeoul);
        LocalDateTime groupBuyEndAt = before.minusHours(2);
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getId()).thenReturn(3L);
        when(groupBuy.getGroupBuyEndAt()).thenReturn(groupBuyEndAt);
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of());
        when(groupBuyRepository.findDueForJudgmentRecovery(
            eq(GroupBuyStatus.OPEN), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(groupBuy));
        when(judgmentSchedule.score(3L)).thenReturn(null);

        scheduler.judgeDueGroupBuys();

        ArgumentCaptor<LocalDateTime> cutoffCaptor =
            ArgumentCaptor.forClass(LocalDateTime.class);
        verify(groupBuyRepository).findDueForJudgmentRecovery(
            eq(GroupBuyStatus.OPEN), cutoffCaptor.capture(), any(Pageable.class));
        assertThat(cutoffCaptor.getValue()).isBetween(
            before.minusMinutes(65), LocalDateTime.now(zoneSeoul).minusMinutes(65));
        verify(judgmentSchedule).schedule(3L, groupBuyEndAt.plusMinutes(5));
        verify(judgmentService, never()).judgeAndPay(3L);
    }

    @Test
    @DisplayName("복구 케이스 - 정상 예약은 재등록하거나 선행 판정하지 않음")
    void Redis에_예약된_공동구매는_복구가_건드리지_않는다() {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getId()).thenReturn(3L);
        when(judgmentSchedule.findDue(any(LocalDateTime.class), eq(100)))
            .thenReturn(Set.of());
        when(groupBuyRepository.findDueForJudgmentRecovery(
            eq(GroupBuyStatus.OPEN), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(groupBuy));
        when(judgmentSchedule.score(3L)).thenReturn(123.0);

        scheduler.judgeDueGroupBuys();

        verify(judgmentSchedule, never()).schedule(eq(3L), any(LocalDateTime.class));
        verify(judgmentService, never()).judgeAndPay(3L);
    }
}
