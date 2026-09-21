package com.moongcheap_backend.demand.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.application.demand.DemandService;
import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.DemandFixture;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.ProductCatalogFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 4-2: Demand 동시 cancel idempotency")
class DemandCancelConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private DemandService demandService;
    @Autowired private DemandRepository demandRepository;
    @Autowired private DemandBoardRepository demandBoardRepository;
    @Autowired private MemberFixture memberFixture;
    @Autowired private ProductCatalogFixture productCatalogFixture;
    @Autowired private DemandFixture demandFixture;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("동일 demandId 50회 동시 cancel 시 정확히 1건 성공, participantCount는 1만 감소")
    void onlyOneCancelSucceedsAndParticipantCountDecrementsOnce() throws Exception {
        int threadCount = 50;
        Long memberId = memberFixture.save("취소유저").getId();
        Long catalogId = productCatalogFixture.save("사과", 5000).getId();
        DemandBoard board = demandFixture.saveBoard(catalogId,
            DemandBoardStatus.GB_GATHERING, 5, LocalDateTime.now().plusDays(3));
        Demand demand = demandFixture.saveWithStatus(memberId, catalogId, board.getId(),
            DemandStatus.ASSIGNED);

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                demandService.cancel(memberId, demand.getId());
                return true;
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.DEMAND_CANCEL_NOT_ALLOWED) throw e;
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);

        Demand refreshed = demandRepository.findById(demand.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(DemandStatus.CANCELED);

        DemandBoard boardRefreshed = demandBoardRepository.findById(board.getId()).orElseThrow();
        assertThat(boardRefreshed.getParticipantCount()).isEqualTo(4);
    }
}
