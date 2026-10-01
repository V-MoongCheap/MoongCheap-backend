package com.moongcheap_backend.product.infrastructure.productCatalog;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, Long> {

    List<ProductCatalog> findByNameContaining(String name, Pageable pageable);

    @Query(value = """
        SELECT * FROM product_catalog
        WHERE LOWER(name) LIKE CONCAT('%', :name, '%') ESCAPE '\\'
          AND status = 'ACTIVE'
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<ProductCatalog> findByNameContainingWithOffset(
        @Param("name") String name,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
}
