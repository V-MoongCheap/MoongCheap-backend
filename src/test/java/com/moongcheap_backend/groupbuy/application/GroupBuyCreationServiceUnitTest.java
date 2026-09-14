package com.moongcheap_backend.groupbuy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.outbox.domain.OutboxEvent;
import com.moongcheap_backend.common.outbox.domain.OutboxEventStatus;
import com.moongcheap_backend.common.outbox.domain.OutboxEventType;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.member.application.SellerPublicService;
import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.product.application.product.ProductPublicService;
import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트 대상: {@link GroupBuyService}의 공동구매 생성 및 Outbox 저장 기능
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("공동구매 생성 - 해피 케이스")
class GroupBuyCreationServiceUnitTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ProductPublicService productPublicService;

    @Mock
    private SellerPublicService sellerPublicService;

    @Mock
    private Product product;

    @Mock
    private Seller seller;

    @InjectMocks
    private GroupBuyService groupBuyService;

    @Test
    void 공동구매와_판정_예약_Outbox를_함께_저장한다() {
        Long productId = 1L;
        Long groupBuyId = 2L;
        Long sellerId = 3L;
        Long catalogId = 4L;
        LocalDateTime saleEndAt = LocalDateTime.of(2026, 9, 10, 12, 0);
        when(productPublicService.getByIdAndStatus(productId, ProductStatus.AWARDED))
            .thenReturn(product);
        when(product.getSellerId()).thenReturn(sellerId);
        when(product.getCatalogId()).thenReturn(catalogId);
        when(product.getMinParticipantCount()).thenReturn(10);
        when(product.getSaleEndAt()).thenReturn(saleEndAt);
        when(sellerPublicService.getByIdAndStatus(sellerId, SellerStatus.APPROVED))
            .thenReturn(seller);
        when(productPublicService.getCatalogNameById(catalogId)).thenReturn("공동구매");
        when(groupBuyRepository.save(any(GroupBuy.class))).thenAnswer(invocation -> {
            GroupBuy groupBuy = invocation.getArgument(0);
            ReflectionTestUtils.setField(groupBuy, "id", groupBuyId);
            return groupBuy;
        });

        groupBuyService.createGroupBuy(productId);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType())
            .isEqualTo(OutboxEventType.GROUP_BUY_JUDGMENT_SCHEDULED);
        assertThat(event.getAggregateId()).isEqualTo(groupBuyId);
        assertThat(event.getScheduledAt()).isEqualTo(saleEndAt.plusMinutes(5));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        verify(product).startSale();
    }
}
