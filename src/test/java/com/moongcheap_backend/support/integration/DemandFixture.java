package com.moongcheap_backend.support.integration;

import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DemandFixture {

    @Autowired
    private DemandRepository demandRepository;

    @Autowired
    private DemandBoardRepository demandBoardRepository;

    public DemandBoard saveBoard(Long catalogId, DemandBoardStatus status, int participantCount,
        LocalDateTime saleEndAt) {
        return demandBoardRepository.save(DemandBoard.builder()
            .catalogId(catalogId)
            .priceMin(10000)
            .priceMax(20000)
            .saleEndAt(saleEndAt)
            .participantCount(participantCount)
            .status(status)
            .build());
    }

    public Demand saveUnassigned(Long memberId, Long catalogId) {
        return demandRepository.save(Demand.builder()
            .memberId(memberId)
            .catalogId(catalogId)
            .payMethodId(null)
            .desiredPriceMin(10000)
            .desiredPriceMax(20000)
            .desireEndAt(LocalDateTime.now().plusDays(1))
            .quantity(1)
            .extraRequirement(null)
            .isSubstitutable(true)
            .build());
    }

    public Demand saveWithStatus(Long memberId, Long catalogId, Long boardId, DemandStatus status) {
        Demand demand = Demand.builder()
            .memberId(memberId)
            .catalogId(catalogId)
            .payMethodId(null)
            .desiredPriceMin(10000)
            .desiredPriceMax(20000)
            .desireEndAt(LocalDateTime.now().plusDays(1))
            .quantity(1)
            .extraRequirement(null)
            .isSubstitutable(true)
            .build();
        forceStatus(demand, status);
        if (boardId != null) forceBoardId(demand, boardId);
        return demandRepository.save(demand);
    }

    private void forceStatus(Demand demand, DemandStatus status) {
        setField(demand, "status", status);
    }

    private void forceBoardId(Demand demand, Long boardId) {
        setField(demand, "demandBoardId", boardId);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
