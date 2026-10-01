package com.moongcheap_backend.product.application.product;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import com.moongcheap_backend.product.infrastructure.product.ProductRepository;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductPublicService {

    private final ProductRepository productRepository;
    private final ProductCatalogRespository productCatalogRespository;

    // 만료시간 이전의 조건에 맞는 상품데이터
    @Transactional(readOnly = true)
    public Product getByIdAndStatus(Long productId, ProductStatus status) {
        return productRepository.findByIdAndStatusAndSaleEndAtAfter(
                productId, status, LocalDateTime.now())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_ORDERABLE));
    }

    @Transactional(readOnly = true)
    public String getCatalogNameById(Long catalogId) {
        return productCatalogRespository.findById(catalogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_CATALOG_NOT_FOUND))
            .getName();
    }
}
