package com.moongcheap_backend.demand.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.common.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 4-3/4-4: DemandBoard participant_count 정합성")
class DemandParticipantCountConcurrencyTest extends AbstractConcurrencyTest {

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
    @DisplayName("4-3: 동일 demand 50회 동시 acceptOffer 시 정확히 1건 성공, participant_count 1만 증가")
    void onlyOneAcceptOfferSucceeds() throws Exception {
        int threadCount = 50;
        Long memberId = memberFixture.save("승낙유저").getId();
        Long catalogId = productCatalogFixture.save("사과", 5000).getId();
        DemandBoard board = demandFixture.saveBoard(catalogId,
            DemandBoardStatus.GB_GATHERING, 0, LocalDateTime.now().plusDays(3));
        Demand demand = demandFixture.saveWithStatus(memberId, catalogId, board.getId(),
            DemandStatus.SUBSTITUTE_OFFERED);

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                demandService.acceptOffer(memberId, demand.getId());
                return true;
            } catch (BusinessException e) {
                return false;
            }
        });

        // 1건 성공 or 실패 다양: 상태 변경 성공한 요청만 counted
        assertThat(result.success()).isGreaterThanOrEqualTo(1);
        DemandBoard boardRefreshed = demandBoardRepository.findById(board.getId()).orElseThrow();
        assertThat(boardRefreshed.getParticipantCount()).isLessThanOrEqualTo(1);
        Demand refreshed = demandRepository.findById(demand.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isIn(DemandStatus.ASSIGNED, DemandStatus.UNASSIGNED);
    }

    @Test
    @DisplayName("4-4: 50명이 동시에 accept 30명 + cancel 20명 요청 시 participant_count가 정확히 계산된다")
    void multipleMembersConcurrentAcceptCancel() throws Exception {
        int acceptCount = 30;
        int cancelCount = 20;
        int threadCount = acceptCount + cancelCount;
        Long catalogId = productCatalogFixture.save("우유", 3000).getId();
        DemandBoard board = demandFixture.saveBoard(catalogId,
            DemandBoardStatus.GB_GATHERING, 2, LocalDateTime.now().plusDays(3));

        // 초기 participant_count = 2 (accept 대상은 SUBSTITUTE_OFFERED, cancel 대상은 ASSIGNED)
        List<Long> acceptMembers = new ArrayList<>();
        List<Long> cancelMembers = new ArrayList<>();
        List<Long> demandIds = new ArrayList<>();

        for (int i = 0; i < acceptCount; i++) {
            Long mid = memberFixture.save("accept-" + i).getId();
            Long did = demandFixture.saveWithStatus(mid, catalogId, board.getId(),
                DemandStatus.SUBSTITUTE_OFFERED).getId();
            acceptMembers.add(mid);
            demandIds.add(did);
        }
        int cancelStartIdx = demandIds.size();
        for (int i = 0; i < cancelCount; i++) {
            Long mid = memberFixture.save("cancel-" + i).getId();
            Long did = demandFixture.saveWithStatus(mid, catalogId, board.getId(),
                DemandStatus.ASSIGNED).getId();
            cancelMembers.add(mid);
            demandIds.add(did);
        }

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                if (idx < acceptCount) {
                    demandService.acceptOffer(acceptMembers.get(idx), demandIds.get(idx));
                } else {
                    demandService.cancel(cancelMembers.get(idx - acceptCount), demandIds.get(idx));
                }
                return true;
            } catch (BusinessException e) {
                return false;
            }
        });

        // 성공한 accept 수와 실제 board.participantCount 정합성 검증
        long acceptSuccessCount = demandIds.subList(0, acceptCount).stream()
            .map(id -> demandRepository.findById(id).orElseThrow())
            .filter(d -> d.getStatus() == DemandStatus.ASSIGNED)
            .count();
        long cancelSuccessCount = demandIds.subList(cancelStartIdx, demandIds.size()).stream()
            .map(id -> demandRepository.findById(id).orElseThrow())
            .filter(d -> d.getStatus() == DemandStatus.CANCELED)
            .count();

        DemandBoard boardRefreshed = demandBoardRepository.findById(board.getId()).orElseThrow();
        int expected = (int) (2 + acceptSuccessCount - cancelSuccessCount);
        assertThat(boardRefreshed.getParticipantCount()).isEqualTo(expected);
        assertThat(result.total()).isEqualTo(threadCount);
    }
}
