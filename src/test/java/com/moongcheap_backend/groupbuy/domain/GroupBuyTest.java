package com.moongcheap_backend.groupbuy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.product.domain.product.Product;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 테스트 대상: {@link GroupBuy} 도메인 엔티티
 */
class GroupBuyTest {

    @Nested
    @DisplayName("해피 케이스")
    class HappyCase {

        @Test
        void 주문이_생성된_인원만큼_현재_참여_인원을_증가시킨다() {
            GroupBuy groupBuy = createGroupBuy(2);

            groupBuy.increaseParticipantCount(3);

            assertThat(groupBuy.getCount()).isEqualTo(5);
        }

        @Test
        void 주문이_취소되면_현재_참여_인원을_한명_감소시킨다() {
            GroupBuy groupBuy = createGroupBuy(2);

            groupBuy.decreaseParticipantCount();

            assertThat(groupBuy.getCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCase {

        @Test
        void 현재_참여_인원이_0이면_감소시킬_수_없다() {
            GroupBuy groupBuy = createGroupBuy(0);

            assertThatThrownBy(groupBuy::decreaseParticipantCount)
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private GroupBuy createGroupBuy(int count) {
        return new GroupBuy(
            mock(Seller.class),
            mock(Product.class),
            "공동구매",
            10,
            count,
            LocalDateTime.now().plusDays(1),
            GroupBuyStatus.OPEN
        );
    }
}
