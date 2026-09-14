package com.moongcheap_backend.product.infrastructure.product;

import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Product> findByIdAndStatusAndSaleEndAtAfter(
        Long id,
        ProductStatus status,
        LocalDateTime currentDateTime
    );
}
