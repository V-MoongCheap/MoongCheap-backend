package com.moongcheap_backend.product.application.product;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRepository;
import com.moongcheap_backend.product.infrastructure.productSearch.ProductCatalogSearchRepository;
import com.moongcheap_backend.product.infrastructure.productSearch.ProductSearchDocument;
import com.moongcheap_backend.product.presentation.productSearch.dto.ProductSearchResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductCatalogSearchRepository productSearchRepository;
    private final ProductCatalogRepository productCatalogRepository;

    public void index(Long catalogId) throws IOException {
        var catalog = productCatalogRepository.findById(catalogId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "카탈로그를 찾을 수 없습니다."));
        productSearchRepository.save(catalog);
    }

    public void indexAll(List<Long> catalogIds) throws IOException {
        List<ProductCatalog> catalogs = productCatalogRepository.findAllById(catalogIds);
        Set<Long> foundIds = catalogs.stream()
            .map(ProductCatalog::getId)
            .collect(Collectors.toSet());
        List<Long> missingIds = catalogIds.stream()
            .filter(id -> !foundIds.contains(id))
            .toList();
        if (!missingIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_CATALOG_NOT_FOUND,
                "존재하지 않는 카탈로그 ID: " + missingIds);
        }
        productSearchRepository.saveAll(catalogs);
    }

    public void delete(Long id) throws IOException {
        productSearchRepository.delete(id);
    }

    public ProductSearchResponse search(String keyword, int page, int size) throws IOException {
        int from = page * size;
        List<ProductSearchDocument> docs =
            productSearchRepository.searchByName(keyword, from, size + 1);
        return ProductSearchResponse.of(docs, page, size);
    }
}
