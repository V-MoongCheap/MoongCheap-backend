package com.moongcheap_backend.groupbuy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 테스트 대상: {@link GroupBuyPublicService}의 주문 소스 조회 및 참여 인원 변경 기능
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyPublicServiceUnitTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @InjectMocks
    private GroupBuyPublicService groupBuyPublicService;

    @Test
    @DisplayName("해피 케이스 - OPEN 공동구매를 주문 소스로 반환한다")
    void OPEN_공동구매를_주문_소스로_반환한다() {
        Long groupBuyId = 1L;
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findByIdWithSellerAndProductForUpdate(groupBuyId))
            .thenReturn(Optional.of(groupBuy));
        when(groupBuy.getStatus()).thenReturn(GroupBuyStatus.OPEN);

        GroupBuy result = groupBuyPublicService.getOrderSource(groupBuyId);

        assertThat(result).isSameAs(groupBuy);
    }

    @Test
    @DisplayName("예외 케이스 - 주문 소스 공동구매가 존재하지 않는다")
    void 주문_소스_공동구매가_없으면_GROUPBUY_NOT_FOUND를_던진다() {
        Long groupBuyId = 1L;
        when(groupBuyRepository.findByIdWithSellerAndProductForUpdate(groupBuyId))
            .thenReturn(Optional.empty());

        assertBusinessException(
            () -> groupBuyPublicService.getOrderSource(groupBuyId),
            ErrorCode.GROUPBUY_NOT_FOUND
        );
    }

    @Test
    @DisplayName("예외 케이스 - 주문 소스 공동구매가 OPEN 상태가 아니다")
    void OPEN_상태가_아니면_GROUPBUY_NOT_OPEN을_던진다() {
        Long groupBuyId = 1L;
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findByIdWithSellerAndProductForUpdate(groupBuyId))
            .thenReturn(Optional.of(groupBuy));
        when(groupBuy.getStatus()).thenReturn(GroupBuyStatus.CLOSED);

        assertBusinessException(
            () -> groupBuyPublicService.getOrderSource(groupBuyId),
            ErrorCode.GROUPBUY_NOT_OPEN
        );
    }

    @Test
    @DisplayName("해피 케이스 - 공동구매 참여 인원을 감소시킨다")
    void 공동구매를_잠근_뒤_참여_인원을_감소시킨다() {
        Long groupBuyId = 1L;
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findByIdForParticipantCountUpdate(groupBuyId))
            .thenReturn(Optional.of(groupBuy));

        groupBuyPublicService.decreaseParticipantCount(groupBuyId);

        verify(groupBuy).decreaseParticipantCount();
    }

    @Test
    @DisplayName("예외 케이스 - 참여 인원을 변경할 공동구매가 존재하지 않는다")
    void 참여_인원을_변경할_공동구매가_없으면_GROUPBUY_NOT_FOUND를_던진다() {
        Long groupBuyId = 1L;
        when(groupBuyRepository.findByIdForParticipantCountUpdate(groupBuyId))
            .thenReturn(Optional.empty());

        assertBusinessException(
            () -> groupBuyPublicService.decreaseParticipantCount(groupBuyId),
            ErrorCode.GROUPBUY_NOT_FOUND
        );
    }

    private void assertBusinessException(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(errorCode);
    }
}
