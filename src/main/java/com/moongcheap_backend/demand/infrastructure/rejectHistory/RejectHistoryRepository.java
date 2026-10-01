package com.moongcheap_backend.demand.infrastructure.rejectHistory;

import com.moongcheap_backend.demand.domain.rejectHistory.RejectHistory;
import com.moongcheap_backend.demand.domain.rejectHistory.RejectHistoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RejectHistoryRepository extends JpaRepository<RejectHistory, RejectHistoryId> {

    @Modifying
    @Query("DELETE FROM RejectHistory r WHERE r.demandId = :demandId")
    void deleteAllByDemandId(@Param("demandId") Long demandId);
}
