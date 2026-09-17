package com.moongcheap_backend.product.application.productCatalog;

import com.moongcheap_backend.category.infrastructure.CategoryRepository;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRespository;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogCreateRequest;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogDto;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogSummaryListDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final ProductCatalogRespository productCatalogRespository;
    private final CategoryRepository categoryRepository;

    // 상품 최신순에 정확히 맞지는 않지만 중요하지 않아 감수함
    @Transactional(readOnly = true)
    public ProductCatalogSummaryListDto getHotProductCatalog(int limit) {
        List<ProductCatalog> catalogs = productCatalogRespository.findAllByOrderByIdDesc(
            PageRequest.of(0, limit)
        );
        return ProductCatalogSummaryListDto.from(catalogs);
    }

    @Transactional
    public Long create(ProductCatalogCreateRequest request) {
        if (request.categoryId() != null
            && !categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        ProductCatalog catalog = ProductCatalog.builder()
            .name(request.name())
            .thumbnailUrl(request.thumbnailUrl())
            .specSummary(request.specSummary())
            .listPrice(request.listPrice())
            .description(request.description())
            .build();
        return productCatalogRespository.save(catalog).getId();
    }

    @Transactional(readOnly = true)
    public ProductCatalogDto getProductCatalogById(Long id) {
        return ProductCatalogDto.from(
            productCatalogRespository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_CATALOG_NOT_FOUND)));
    }
}
