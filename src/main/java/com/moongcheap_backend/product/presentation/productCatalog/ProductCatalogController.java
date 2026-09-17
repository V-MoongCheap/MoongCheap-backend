package com.moongcheap_backend.product.presentation.productCatalog;


import com.moongcheap_backend.common.response.IdResponse;
import com.moongcheap_backend.common.security.SessionPrincipal;
import com.moongcheap_backend.product.application.productCatalog.ProductCatalogService;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogCreateRequest;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogDto;
import com.moongcheap_backend.product.presentation.productCatalog.dto.ProductCatalogSummaryListDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product · 상품 도감", description = "상품 도감 CRUD")
@RestController
@RequestMapping("/api/product-catalog")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;

    @Operation(summary = "상위 상품 도감 조회", description = "FN-B03-01. 상위 9개의 상품 도감 조회.")
    @GetMapping
    public ProductCatalogSummaryListDto getHotProductCatalogs(
        SessionPrincipal principal) {
        return productCatalogService.getHotProductCatalog(9);
    }

    @Operation(summary = "상품 도감 정보 조회", description = "FN-B08-01. 상품 도감 상세 조회. 수요 보드의 경우 별도 api 요청(/api/demand-boards/catalog/{catalogId}")
    @GetMapping("/{id}")
    public ProductCatalogDto getProductCatalogById(
        SessionPrincipal principal,
        @PathVariable Long id) {
        return productCatalogService.getProductCatalogById(id);
    }

    @Operation(summary = "상품 도감 생성", description = "FN-B10-01. 새로운 상품 도감 생성")
    @PostMapping
    public ResponseEntity<IdResponse> create(
        @RequestBody @Valid ProductCatalogCreateRequest request) {
        return ResponseEntity.ok(IdResponse.of(productCatalogService.create(request)));
    }
}
