package com.moongcheap_backend.demand.infrastructure.demand;

import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
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

public interface DemandRepository extends JpaRepository<Demand, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Demand> findAllByDemandBoardIdAndStatus(
        Long demandBoardId,
        DemandStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Demand d WHERE d.id = :id")
    Optional<Demand> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Demand d WHERE d.id = :id AND d.isSubstitutable = true")
    Optional<Demand> findByIdAndSubstitutableForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Demand d "
        + "WHERE d.id = :id AND d.memberId = :memberId AND d.status = :status")
    Optional<Demand> findByIdAndStatusForUpdate(
        @Param("id") Long id,
        @Param("memberId") Long memberId,
        @Param("status") DemandStatus status);

    boolean existsByMemberIdAndCatalogIdAndStatusIn(
        Long memberId, Long catalogId, Collection<DemandStatus> statuses);

    boolean existsByMemberIdAndDemandBoardIdAndStatusIn(
        Long memberId, Long demandBoardId, Collection<DemandStatus> statuses);

    @Modifying
    @Query(value = """
        UPDATE demand
           SET status = 'EXPIRED', updated_at = :threshold
         WHERE id IN (
             SELECT id FROM demand
              WHERE status IN ('UNASSIGNED','SUBSTITUTE_OFFERED') AND desire_end_at < :threshold
              LIMIT :chunkSize
              FOR UPDATE SKIP LOCKED
         )
        """, nativeQuery = true)
    int expireChunk(
        @Param("threshold") LocalDateTime threshold,
        @Param("chunkSize") int chunkSize);

    @Modifying
    @Query(value = """
        UPDATE demand
           SET status = 'ASSIGNED',
               demand_board_id = :boardId,
               desire_end_at = :desireEndAt,
               updated_at = :updatedAt
         WHERE id IN (:demandIds)
           AND status = 'UNASSIGNED'
        """, nativeQuery = true)
    int assignToBoard(
        @Param("boardId") Long boardId,
        @Param("desireEndAt") LocalDateTime desireEndAt,
        @Param("updatedAt") LocalDateTime updatedAt,
        @Param("demandIds") List<Long> demandIds);

    @Modifying
    @Query(value = """
        WITH board_update AS (
            UPDATE demand_board
               SET participant_count = participant_count + :count
             WHERE id = :boardId AND status = 'GB_GATHERING'
            RETURNING sale_end_at
        )
        UPDATE demand
           SET status = 'ASSIGNED',
               demand_board_id = :boardId,
               desire_end_at = (SELECT sale_end_at FROM board_update),
               updated_at = :updatedAt
         WHERE id IN (:demandIds)
           AND status = 'UNASSIGNED'
           AND EXISTS (SELECT 1 FROM board_update)
        """, nativeQuery = true)
    int assignToExistingBoard(
        @Param("boardId") Long boardId,
        @Param("count") int count,
        @Param("updatedAt") LocalDateTime updatedAt,
        @Param("demandIds") List<Long> demandIds);

    @Modifying
    @Query("UPDATE Demand d SET d.status = :newStatus, d.updatedAt = :now "
        + "WHERE d.demandBoardId IN :boardIds AND d.status = :expectedStatus")
    int transitionStatusBulkByBoardIds(
        @Param("boardIds") Collection<Long> boardIds,
        @Param("expectedStatus") DemandStatus expectedStatus,
        @Param("newStatus") DemandStatus newStatus,
        @Param("now") LocalDateTime now);

}
