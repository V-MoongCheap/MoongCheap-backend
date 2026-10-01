package com.moongcheap_backend.product.application.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.application.product.ProductService;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRepository;
import com.moongcheap_backend.product.infrastructure.productSearch.ProductCatalogSearchRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductServiceFailureTest {

    @Mock private ProductCatalogSearchRepository productSearchRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private ProductService service;

    @Nested
    @DisplayName("index - 실패")
    class IndexTest {

        @Test
        void 존재하지_않는_catalogId로_단건_인덱싱한다() {
            when(productCatalogRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.index(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("indexAll - 실패")
    class IndexAllTest {

        @Test
        void 일부_존재하지_않는_catalogId가_포함된_목록으로_일괄_인덱싱한다() {
            ProductCatalog catalog = mock(ProductCatalog.class);
            when(catalog.getId()).thenReturn(1L);
            when(productCatalogRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(catalog));

            assertThatThrownBy(() -> service.indexAll(List.of(1L, 2L, 3L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_CATALOG_NOT_FOUND)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("2", "3"));
        }
    }
}
