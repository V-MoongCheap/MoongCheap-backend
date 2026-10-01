package com.moongcheap_backend.product.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.category.infrastructure.CategoryRepository;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.application.productCatalog.ProductCatalogService;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceFailureTest {

    @Mock private ProductCatalogRespository productCatalogRespository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductCatalogService service;

    @Nested
    @DisplayName("getProductCatalogById - 실패")
    class GetProductCatalogByIdTest {

        @Test
        void 존재하지_않는_ID로_카탈로그를_단건_조회한다() {
            when(productCatalogRespository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProductCatalogById(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_CATALOG_NOT_FOUND);
        }
    }
}
