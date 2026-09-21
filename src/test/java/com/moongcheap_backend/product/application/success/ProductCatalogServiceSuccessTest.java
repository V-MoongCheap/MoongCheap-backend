package com.moongcheap_backend.product.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.category.infrastructure.CategoryRepository;
import com.moongcheap_backend.product.application.productCatalog.ProductCatalogService;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogDto;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogSummaryListDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceSuccessTest {

    @Mock private ProductCatalogRespository productCatalogRespository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductCatalogService service;

    @Nested
    @DisplayName("getHotProductCatalog - 성공")
    class GetHotProductCatalogTest {

        @Test
        void limit_5로_최신_상품_카탈로그_목록을_조회한다() {
            ProductCatalog catalog = mock(ProductCatalog.class);
            when(catalog.getId()).thenReturn(1L);
            when(catalog.getName()).thenReturn("상품A");
            when(productCatalogRespository.findAllByOrderByIdDesc(any(Pageable.class)))
                .thenReturn(List.of(catalog));

            ProductCatalogSummaryListDto result = service.getHotProductCatalog(5);

            assertThat(result.list()).hasSize(1);
            verify(productCatalogRespository).findAllByOrderByIdDesc(
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 5));
        }

        @Test
        void 등록된_카탈로그가_없는_상태에서_조회한다() {
            when(productCatalogRespository.findAllByOrderByIdDesc(any(Pageable.class)))
                .thenReturn(List.of());

            ProductCatalogSummaryListDto result = service.getHotProductCatalog(5);

            assertThat(result.list()).isEmpty();
            assertThat(result.totalCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getProductCatalogById - 성공")
    class GetProductCatalogByIdTest {

        @Test
        void 유효한_ID로_카탈로그를_단건_조회한다() {
            ProductCatalog catalog = mock(ProductCatalog.class);
            when(catalog.getId()).thenReturn(1L);
            when(catalog.getName()).thenReturn("상품A");
            when(productCatalogRespository.findById(1L)).thenReturn(Optional.of(catalog));

            ProductCatalogDto result = service.getProductCatalogById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("상품A");
        }
    }
}
