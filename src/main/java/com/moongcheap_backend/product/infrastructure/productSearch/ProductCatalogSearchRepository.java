package com.moongcheap_backend.product.infrastructure.productSearch;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.product.domain.productCatalog.ProductCatalogStatus;
import com.moongcheap_backend.product.infrastructure.productCatalog.ProductCatalogRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductCatalogSearchRepository {

    private static final String ALIAS = "product_catalog";
    private static final String CIRCUIT_BREAKER_NAME = "opensearch";

    private final OpenSearchClient openSearchClient;
    private final ProductCatalogRepository productCatalogRepository;

    public void save(ProductCatalog catalog) throws IOException {
        openSearchClient.index(i -> i
            .index(ALIAS)
            .id(String.valueOf(catalog.getId()))
            .document(ProductSearchDocument.from(catalog))
            .refresh(Refresh.False)
        );
    }

    public void saveAll(List<ProductCatalog> catalogs) throws IOException {
        if (catalogs == null || catalogs.isEmpty()) {
            return;
        }

        List<BulkOperation> operations = catalogs.stream()
            .map(c -> BulkOperation.of(op ->
                op.index(idx ->
                    idx.index(ALIAS)
                        .id(String.valueOf(c.getId()))
                        .document(ProductSearchDocument.from(c)))))
            .toList();

        BulkResponse response = openSearchClient.bulk(b -> b.operations(operations));

        if (response.errors()) {
            long count = response.items().stream()
                .filter(i -> i.error() != null)
                .peek(i -> log.error(
                    "Bulk search index failed: id={}, reason={}",
                    i.id(),
                    i.error().reason()
                ))
                .count();

            throw new BusinessException(
                ErrorCode.SEARCH_INDEX_FAILED,
                "상품 검색 인덱싱 실패: %d/%d건".formatted(count, catalogs.size())
            );
        }
    }

    public void delete(Long id) throws IOException {
        openSearchClient.delete(d -> d
            .index(ALIAS)
            .id(String.valueOf(id))
            .refresh(Refresh.False)
        );
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "searchByNameFallback")
    public List<ProductSearchDocument> searchByName(String keyword, int from, int size)
        throws IOException {
        Query query = Query.of(q -> q
            .bool(b -> b
                .filter(f -> f.term(t -> t.field("status")
                    .value(FieldValue.of(ProductCatalogStatus.ACTIVE.name()))))
                .must(m -> m.bool(inner -> inner
                    .should(s -> s.multiMatch(mm -> mm
                        .query(keyword)
                        .type(TextQueryType.CrossFields)
                        .fields("name^3", "specSummary^2")
                        .minimumShouldMatch("2<75%")))
                    .should(s -> s.multiMatch(mm -> mm
                        .query(keyword)
                        .type(TextQueryType.CrossFields)
                        .fields("name.ngram^1.5", "specSummary.ngram^0.5")
                        .operator(Operator.And)))
                    .minimumShouldMatch("1")
                ))
            )
        );
        SearchResponse<ProductSearchDocument> response = openSearchClient.search(s -> s
                .index(ALIAS)
                .query(query)
                .from(from)
                .size(size),
            ProductSearchDocument.class);
        return response.hits().hits().stream()
            .map(Hit::source)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<ProductSearchDocument> searchByNameFallback(String keyword, int from, int size,
        Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("OpenSearch 서킷 OPEN, PostgreSQL 폴백 keyword={}", keyword);
        } else {
            log.warn("OpenSearch 검색 실패, PostgreSQL 폴백 keyword={} reason={}", keyword,
                t.getMessage());
        }
        String escaped = keyword.toLowerCase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return productCatalogRepository
            .findByNameContainingWithOffset(escaped, from, size).stream()
            .map(ProductSearchDocument::from)
            .toList();
    }

}
