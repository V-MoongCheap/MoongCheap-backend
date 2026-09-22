package com.moongcheap_backend.product.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.product.application.product.ProductService;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRepository;
import com.moongcheap_backend.product.infrastructure.productSearch.ProductCatalogSearchRepository;
import com.moongcheap_backend.product.infrastructure.productSearch.ProductSearchDocument;
import com.moongcheap_backend.product.presentation.productSearch.dto.ProductSearchResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceSuccessTest {

    @Mock private ProductCatalogSearchRepository productSearchRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private ProductService service;

    private ProductSearchDocument document(Long id) {
        return new ProductSearchDocument(id, "상품" + id, "요약", 10000, null, "ACTIVE");
    }

    @Nested
    @DisplayName("index - 성공")
    class IndexTest {

        @Test
        void 유효한_catalogId로_단건_인덱싱한다() throws IOException {
            ProductCatalog catalog = mock(ProductCatalog.class);
            when(productCatalogRepository.findById(1L)).thenReturn(Optional.of(catalog));

            service.index(1L);

            verify(productSearchRepository).save(catalog);
        }
    }

    @Nested
    @DisplayName("indexAll - 성공")
    class IndexAllTest {

        @Test
        void 모두_존재하는_catalogId_목록으로_일괄_인덱싱한다() throws IOException {
            ProductCatalog catalog1 = mock(ProductCatalog.class);
            ProductCatalog catalog2 = mock(ProductCatalog.class);
            when(catalog1.getId()).thenReturn(1L);
            when(catalog2.getId()).thenReturn(2L);
            when(productCatalogRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(catalog1, catalog2));

            service.indexAll(List.of(1L, 2L));

            verify(productSearchRepository).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("delete - 성공")
    class DeleteTest {

        @Test
        void 유효한_ID로_검색_인덱스를_삭제한다() throws IOException {
            service.delete(1L);

            verify(productSearchRepository).delete(1L);
        }
    }

    @Nested
    @DisplayName("search - 성공")
    class SearchTest {

        @Test
        void 키워드로_상품을_검색한다() throws IOException {
            int page = 0;
            int size = 2;
            when(productSearchRepository.searchByName("키워드", page * size, size + 1))
                .thenReturn(List.of(document(1L), document(2L), document(3L)));

            ProductSearchResponse result = service.search("키워드", page, size);

            assertThat(result.hasNext()).isTrue();
            assertThat(result.products()).hasSize(size);
            verify(productSearchRepository).searchByName("키워드", 0, size + 1);
        }

        @Test
        void 결과가_페이지_크기_이하로_반환된다() throws IOException {
            int page = 0;
            int size = 5;
            when(productSearchRepository.searchByName("키워드", page * size, size + 1))
                .thenReturn(List.of(document(1L), document(2L)));

            ProductSearchResponse result = service.search("키워드", page, size);

            assertThat(result.hasNext()).isFalse();
            assertThat(result.products()).hasSize(2);
        }
    }
}
