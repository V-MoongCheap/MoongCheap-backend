package com.moongcheap_backend.groupbuy.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.groupbuy.domain.GroupBuy;
import com.moongcheap_backend.groupbuy.domain.GroupBuyStatus;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyRepository;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyDetailResponse;
import com.moongcheap_backend.groupbuy.presentation.dto.GroupBuyListResponse;
import com.moongcheap_backend.product.domain.product.Product;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 테스트 대상: {@link GroupBuyService}의 목록 및 상세 조회 기능
 */
@ExtendWith(MockitoExtension.class)
class GroupBuyServiceUnitTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @InjectMocks
    private GroupBuyService groupBuyService;

    @Nested
    @DisplayName("공동구매 목록 조회 - 해피 케이스")
    class GetAllTest {

        @Test
        void 열린_공동구매_목록을_페이지로_반환한다() {
            Pageable pageable = PageRequest.of(0, 20);
            Product product = org.mockito.Mockito.mock(Product.class);
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            when(groupBuy.getTitle()).thenReturn("사과 공동구매");
            when(groupBuy.getProduct()).thenReturn(product);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/apple.jpg");
            when(product.getUnitPrice()).thenReturn(10_000);
            when(groupBuyRepository.findAllByStatus(GroupBuyStatus.OPEN, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(groupBuy), pageable, 1));

            Page<GroupBuyListResponse> result = groupBuyService.getAll(pageable);

            GroupBuyListResponse response = result.getContent().getFirst();
            assertAll(
                () -> assertEquals(1, result.getTotalElements()),
                () -> assertEquals("사과 공동구매", response.title()),
                () -> assertEquals("https://example.com/apple.jpg", response.imageUrl()),
                () -> assertEquals(10_000, response.price())
            );
            verify(groupBuyRepository).findAllByStatus(GroupBuyStatus.OPEN, pageable);
        }
    }

    @Nested
    @DisplayName("공동구매 상세 조회 - 해피 케이스")
    class GetByIdTest {

        @Test
        void 공동구매와_상품_정보를_반환한다() {
            Long groupBuyId = 10L;
            LocalDate deliveryDate = LocalDate.of(2026, 9, 15);
            LocalDateTime saleEndAt = LocalDateTime.of(2026, 9, 12, 23, 59);
            Product product = org.mockito.Mockito.mock(Product.class);
            GroupBuy groupBuy = org.mockito.Mockito.mock(GroupBuy.class);
            when(groupBuy.getTitle()).thenReturn("사과 공동구매");
            when(groupBuy.getTargetCount()).thenReturn(100);
            when(groupBuy.getProduct()).thenReturn(product);
            when(product.getThumbnailUrl()).thenReturn("https://example.com/apple.jpg");
            when(product.getUnitPrice()).thenReturn(10_000);
            when(product.getShippingFee()).thenReturn(3_000);
            when(product.getDeliveryDate()).thenReturn(deliveryDate);
            when(product.getSaleEndAt()).thenReturn(saleEndAt);
            when(product.getTotalQuantity()).thenReturn(120);
            when(groupBuyRepository.findDetailById(groupBuyId)).thenReturn(Optional.of(groupBuy));

            GroupBuyDetailResponse result = groupBuyService.getById(groupBuyId);

            assertAll(
                () -> assertEquals("사과 공동구매", result.title()),
                () -> assertEquals("https://example.com/apple.jpg", result.imageUrl()),
                () -> assertEquals(10_000, result.price()),
                () -> assertEquals(100, result.targetCount()),
                () -> assertEquals(3_000, result.productInfo().shippingFee()),
                () -> assertEquals(deliveryDate, result.productInfo().deliveryDate()),
                () -> assertEquals(saleEndAt, result.productInfo().saleEndAt()),
                () -> assertEquals(120, result.productInfo().totalQuantity())
            );
            verify(groupBuyRepository).findDetailById(groupBuyId);
        }
    }
}
