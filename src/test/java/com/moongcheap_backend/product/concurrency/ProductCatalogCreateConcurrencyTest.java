package com.moongcheap_backend.product.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("동시성 5-1: ProductCatalog 동일 name 동시 생성")
class ProductCatalogCreateConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private ProductCatalogRespository productCatalogRespository;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("동일 name으로 50회 동시 create 시 정확히 1건만 성공, 유니크 제약이 나머지를 거부")
    void uniqueConstraintPreventsDuplicateName() throws Exception {
        int threadCount = 50;
        String uniqueName = "동시성테스트카탈로그";

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                productCatalogRespository.save(ProductCatalog.builder()
                    .name(uniqueName)
                    .specSummary("규격")
                    .listPrice(1000)
                    .thumbnailUrl(null)
                    .description(null)
                    .build());
                return true;
            } catch (DataIntegrityViolationException e) {
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
    }
}
