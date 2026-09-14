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
           SET status = 'EXPIRED', processed_at = :threshold
         WHERE id IN (
             SELECT id FROM demand
              WHERE status = 'UNASSIGNED' AND desire_end_at < :threshold
              LIMIT :chunkSize
              FOR UPDATE SKIP LOCKED
         )
        """, nativeQuery = true)
    int expireChunk(
        @Param("threshold") LocalDateTime threshold,
        @Param("chunkSize") int chunkSize);
}
