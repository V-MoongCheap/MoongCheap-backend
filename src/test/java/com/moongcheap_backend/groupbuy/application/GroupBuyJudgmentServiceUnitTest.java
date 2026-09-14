package com.moongcheap_backend.groupbuy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.product.domain.product.Product;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 테스트 대상: {@link GroupBuyJudgmentService}의 만료 공동구매 판정 기능
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyJudgmentServiceUnitTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @InjectMocks
    private GroupBuyJudgmentService groupBuyJudgmentService;

    @Test
    @DisplayName("해피 케이스 - 목표 인원 달성으로 모집 완료 판정")
    void 목표_인원에_도달하면_모집_완료로_판정한다() {
        Long groupBuyId = 1L;
        GroupBuy groupBuy = createGroupBuy(10, 10, LocalDateTime.now().minusDays(1));
        when(groupBuyRepository.findExpiredByIdForJudgment(
            eq(groupBuyId), eq(GroupBuyStatus.OPEN), any(LocalDateTime.class)))
            .thenReturn(Optional.of(groupBuy));

        groupBuyJudgmentService.judgeAndPay(groupBuyId);

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.RECRUITMENT_COMPLETED);
    }

    @Test
    @DisplayName("해피 케이스 - 목표 인원 미달로 실패 판정")
    void 목표_인원_미달로_기한이_만료되면_실패로_판정한다() {
        Long groupBuyId = 1L;
        GroupBuy groupBuy = createGroupBuy(10, 9, LocalDateTime.now().minusDays(1));
        when(groupBuyRepository.findExpiredByIdForJudgment(
            eq(groupBuyId), eq(GroupBuyStatus.OPEN), any(LocalDateTime.class)))
            .thenReturn(Optional.of(groupBuy));

        groupBuyJudgmentService.judgeAndPay(groupBuyId);

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.FAILED);
    }

    @Test
    @DisplayName("예외 케이스 - 판정 대상 공동구매가 존재하지 않는다")
    void 판정할_공동구매가_없으면_GROUPBUY_NOT_FOUND를_던진다() {
        Long groupBuyId = 1L;
        when(groupBuyRepository.findExpiredByIdForJudgment(
            eq(groupBuyId), eq(GroupBuyStatus.OPEN), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupBuyJudgmentService.judgeAndPay(groupBuyId))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.GROUPBUY_NOT_FOUND);
    }

    private GroupBuy createGroupBuy(int targetCount, int count, LocalDateTime endAt) {
        return new GroupBuy(
            mock(Seller.class),
            mock(Product.class),
            "공동구매",
            targetCount,
            count,
            endAt,
            GroupBuyStatus.OPEN
        );
    }
}
