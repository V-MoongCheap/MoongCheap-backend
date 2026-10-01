package com.moongcheap_backend.product.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.application.product.ProductPublicService;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublicServiceFailureTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCatalogRespository productCatalogRespository;

    @InjectMocks
    private ProductPublicService service;

    @Nested
    @DisplayName("getByIdAndStatus - 실패")
    class GetByIdAndStatusTest {

        @Test
        void 만료되었거나_status가_일치하지_않는_상품을_조회한다() {
            when(productRepository.findByIdAndStatusAndSaleEndAtAfter(
                eq(1L), eq(ProductStatus.BIDDING), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByIdAndStatus(1L, ProductStatus.BIDDING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_ORDERABLE);
        }
    }

    @Nested
    @DisplayName("getCatalogNameById - 실패")
    class GetCatalogNameByIdTest {

        @Test
        void 존재하지_않는_카탈로그의_이름을_조회한다() {
            when(productCatalogRespository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCatalogNameById(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_CATALOG_NOT_FOUND);
        }
    }
}
