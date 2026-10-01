package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogFixture {

    @Autowired
    private ProductCatalogRespository productCatalogRespository;

    public ProductCatalog save(String name, Integer listPrice) {
        return productCatalogRespository.save(ProductCatalog.builder()
            .name(name)
            .specSummary("규격 요약")
            .listPrice(listPrice)
            .thumbnailUrl("https://cdn.example.com/thumb.jpg")
            .description("설명")
            .build());
    }
}
