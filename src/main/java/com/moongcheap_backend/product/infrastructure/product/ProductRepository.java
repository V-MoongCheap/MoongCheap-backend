package com.moongcheap_backend.product.infrastructure.product;

import com.moongcheap_backend.product.domain.product.Product;
import com.moongcheap_backend.product.domain.product.ProductStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Query("SELECT p.demandBoardId, p.id, p.unitPrice FROM Product p "
        + "WHERE p.demandBoardId IN :boardIds AND p.status IN :statuses")
    List<Object[]> findAwardedIdAndUnitPriceByBoardIds(
        @Param("boardIds") List<Long> boardIds,
        @Param("statuses") List<ProductStatus> statuses);

    default Map<Long, List<Long>> findProductIdsGroupedByBoardId(
        List<Long> boardIds, ProductStatus status) {
        return findBoardIdAndIdByBoardIdsAndStatus(boardIds, status).stream()
            .collect(Collectors.groupingBy(
                row -> (Long) row[0],
                Collectors.mapping(row -> (Long) row[1], Collectors.toList())));
    }

    @Modifying
    @Query(value = """
        WITH locked_products AS (
            SELECT id
              FROM product
             WHERE id IN (:ids)
               AND status = :expectedStatus
             ORDER BY id
             FOR UPDATE
        )
        UPDATE product
           SET status = :newStatus,
               updated_at = :now
         WHERE id IN (SELECT id FROM locked_products)
        """, nativeQuery = true)
    int transitionStatusBulk(
        @Param("ids") List<Long> ids,
        @Param("expectedStatus") String expectedStatus,
        @Param("newStatus") String newStatus,
        @Param("now") LocalDateTime now);

    default int transitionStatusBulk(
        List<Long> ids,
        ProductStatus expectedStatus,
        ProductStatus newStatus,
        LocalDateTime now) {
        return transitionStatusBulk(ids, expectedStatus.name(), newStatus.name(), now);
    }

    @Modifying
    @Query(value = """
        WITH locked_products AS (
            SELECT id
              FROM product
             WHERE id IN (:ids)
               AND demand_board_id = :boardId
               AND status = :expectedStatus
             ORDER BY id
             FOR UPDATE
        )
        UPDATE product
           SET status = :newStatus,
               updated_at = :now
         WHERE id IN (SELECT id FROM locked_products)
        """, nativeQuery = true)
    int transitionStatusBulkForBoard(
        @Param("ids") List<Long> ids,
        @Param("boardId") Long boardId,
        @Param("expectedStatus") String expectedStatus,
        @Param("newStatus") String newStatus,
        @Param("now") LocalDateTime now);

    default int transitionStatusBulkForBoard(
        List<Long> ids,
        Long boardId,
        ProductStatus expectedStatus,
        ProductStatus newStatus,
        LocalDateTime now) {
        return transitionStatusBulkForBoard(
            ids, boardId, expectedStatus.name(), newStatus.name(), now);
    }

    @Modifying
    @Query("UPDATE Product p SET p.status = :newStatus, p.updatedAt = :now "
        + "WHERE p.id = :id "
        + "  AND p.demandBoardId = :boardId "
        + "  AND p.status = :expectedStatus")
    int transitionStatusForBoard(
        @Param("id") Long id,
        @Param("boardId") Long boardId,
        @Param("expectedStatus") ProductStatus expectedStatus,
        @Param("newStatus") ProductStatus newStatus,
        @Param("now") LocalDateTime now);
}
