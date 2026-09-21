package com.moongcheap_backend.product.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.product.application.product.ProductPublicService;
import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
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
class ProductPublicServiceSuccessTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCatalogRespository productCatalogRespository;

    @InjectMocks
    private ProductPublicService service;

    @Nested
    @DisplayName("getByIdAndStatus - 성공")
    class GetByIdAndStatusTest {

        @Test
        void 유효한_status와_미래_saleEndAt을_가진_상품을_조회한다() {
            Product product = mock(Product.class);
            when(productRepository.findByIdAndStatusAndSaleEndAtAfter(
                eq(1L), eq(ProductStatus.BIDDING), any(LocalDateTime.class)))
                .thenReturn(Optional.of(product));

            Product result = service.getByIdAndStatus(1L, ProductStatus.BIDDING);

            assertThat(result).isSameAs(product);
        }
    }

    @Nested
    @DisplayName("getCatalogNameById - 성공")
    class GetCatalogNameByIdTest {

        @Test
        void 유효한_ID로_카탈로그_이름을_조회한다() {
            ProductCatalog catalog = mock(ProductCatalog.class);
            when(catalog.getName()).thenReturn("상품A");
            when(productCatalogRespository.findById(1L)).thenReturn(Optional.of(catalog));

            String result = service.getCatalogNameById(1L);

            assertThat(result).isEqualTo("상품A");
        }
    }
}
