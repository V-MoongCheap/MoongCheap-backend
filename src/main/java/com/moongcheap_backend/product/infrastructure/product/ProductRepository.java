package com.moongcheap_backend.product.infrastructure.product;

import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Product> findByIdAndStatusAndSaleEndAtAfter(
        Long id,
        ProductStatus status,
        LocalDateTime currentDateTime
    );
    @Query("SELECT p.id FROM Product p WHERE p.demandBoardId = :demandBoardId AND p.status = :status")
    List<Long> findIdsByDemandBoardIdAndStatus(
        @Param("demandBoardId") Long demandBoardId,
        @Param("status") ProductStatus status);

    @Query("SELECT p.demandBoardId, p.id FROM Product p "
        + "WHERE p.demandBoardId IN :boardIds AND p.status = :status")
    List<Object[]> findBoardIdAndIdByBoardIdsAndStatus(
        @Param("boardIds") List<Long> boardIds,
        @Param("status") ProductStatus status);

    default Map<Long, List<Long>> findProductIdsGroupedByBoardId(
        List<Long> boardIds, ProductStatus status) {
        return findBoardIdAndIdByBoardIdsAndStatus(boardIds, status).stream()
            .collect(Collectors.groupingBy(
                row -> (Long) row[0],
                Collectors.mapping(row -> (Long) row[1], Collectors.toList())));
    }

    @Modifying
    @Query("UPDATE Product p SET p.status = :newStatus, p.updatedAt = :now "
        + "WHERE p.id IN :ids AND p.status = :expectedStatus")
    int transitionStatusBulk(
        @Param("ids") List<Long> ids,
        @Param("expectedStatus") ProductStatus expectedStatus,
        @Param("newStatus") ProductStatus newStatus,
        @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Product p SET p.status = :newStatus, p.updatedAt = :now "
        + "WHERE p.id IN :ids "
        + "  AND p.demandBoardId = :boardId "
        + "  AND p.status = :expectedStatus")
    int transitionStatusBulkForBoard(
        @Param("ids") List<Long> ids,
        @Param("boardId") Long boardId,
        @Param("expectedStatus") ProductStatus expectedStatus,
        @Param("newStatus") ProductStatus newStatus,
        @Param("now") LocalDateTime now);
}
