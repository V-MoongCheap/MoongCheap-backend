package com.moongcheap_backend.product.presentation.productSearch;

import com.moongcheap_backend.product.application.product.ProductService;
import com.moongcheap_backend.product.presentation.productSearch.dto.ProductBulkIndexRequest;
import com.moongcheap_backend.product.presentation.productSearch.dto.ProductIndexRequest;
import com.moongcheap_backend.product.presentation.productSearch.dto.ProductSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product", description = "상품 검색/색인")
@RestController
@RequestMapping("/api/products-search")
@RequiredArgsConstructor
public class ProductSearchController {

    private final ProductService productService;

    @Operation(summary = "상품 색인", description = "catalogId로 ProductCatalog를 조회해 OpenSearch에 upsert.")
    @PostMapping("/internal")
    public ResponseEntity<Void> index(@Valid @RequestBody ProductIndexRequest request)
        throws IOException {
        productService.index(request.catalogId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상품 일괄 색인", description = "catalogId 목록으로 ProductCatalog를 조회해 OpenSearch에 일괄 upsert.")
    @PostMapping("/internal/bulk")
    public ResponseEntity<Void> indexAll(@Valid @RequestBody ProductBulkIndexRequest request)
        throws IOException {
        productService.indexAll(request.catalogIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상품 색인 삭제", description = "OpenSearch에서 상품 문서를 id로 삭제.")
    @DeleteMapping("/internal/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws IOException {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상품 검색", description = "상품명/규격 검색. page/size 페이지네이션 지원 (기본 size=20).")
    @GetMapping("/search")
    public ResponseEntity<ProductSearchResponse> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) throws IOException {
        return ResponseEntity.ok(productService.search(q, page, size));
    }
}
