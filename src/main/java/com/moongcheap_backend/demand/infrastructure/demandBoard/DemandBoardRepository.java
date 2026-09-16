package com.moongcheap_backend.demand.infrastructure.demandBoard;

import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DemandBoardRepository extends JpaRepository<DemandBoard, Long> {

    boolean existsByCatalogIdAndStatusIn(Long catalogId, Collection<DemandBoardStatus> statuses);

    boolean existsByIdAndStatus(Long id, DemandBoardStatus status);

    boolean existsByIdAndStatusAndCatalogId(Long id, DemandBoardStatus status, Long catalogId);

    @Modifying
    @Query("UPDATE DemandBoard db SET db.participantCount = db.participantCount - 1 WHERE db.id = :id AND db.participantCount > 0")
    int decrementParticipantCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE DemandBoard db SET db.participantCount = db.participantCount + 1 "
        + "WHERE db.id = :id AND db.status = :status AND db.saleEndAt > CURRENT_TIMESTAMP")
    int increaseParticipantCountIfActive(
        @Param("id") Long id, @Param("status") DemandBoardStatus status);

    @Modifying
    @Query("UPDATE DemandBoard db SET db.status = :newStatus, db.judgedAt = :judgedAt "
        + "WHERE db.id = :id AND db.status = :expectedStatus")
    int markAwarded(
        @Param("id") Long id,
        @Param("expectedStatus") DemandBoardStatus expectedStatus,
        @Param("newStatus") DemandBoardStatus newStatus,
        @Param("judgedAt") LocalDateTime judgedAt);

    @Modifying
    @Query("UPDATE DemandBoard db SET db.status = :newStatus, db.updatedAt = :now "
        + "WHERE db.id IN :ids AND db.status = :expectedStatus")
    int transitionStatusBulk(
        @Param("ids") Collection<Long> ids,
        @Param("expectedStatus") DemandBoardStatus expectedStatus,
        @Param("newStatus") DemandBoardStatus newStatus,
        @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT db FROM DemandBoard db WHERE db.id = :id AND db.status IN :statuses")
    Optional<DemandBoard> findByIdAndStatusInForUpdate(@Param("id") Long id,
        @Param("statuses") Collection<DemandBoardStatus> statuses);

    @Query(value = """
        SELECT * FROM demand_board
         WHERE status = 'GB_GATHERING' AND sale_end_at < :threshold
         LIMIT :chunkSize
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<DemandBoard> findOverdueGatheringChunk(
        @Param("threshold") LocalDateTime threshold,
        @Param("chunkSize") int chunkSize);


    @Modifying
    @Query(value = """
        WITH demand_update AS (
            UPDATE demand
               SET status = 'FAILED', updated_at = :threshold
             WHERE demand_board_id IN (:boardIds)
               AND status IN ('ASSIGNED', 'PAYMENT_PENDING')
        )
        UPDATE demand_board
           SET status = 'GB_CANCELED', updated_at = :threshold
         WHERE id IN (:boardIds)
        """, nativeQuery = true)
    int cancelBoardsAndFailDemands(
        @Param("boardIds") List<Long> boardIds,
        @Param("threshold") LocalDateTime threshold);
}
