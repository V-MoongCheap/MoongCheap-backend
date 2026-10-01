package com.moongcheap_backend.demand.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.demand.application.demand.DemandService;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.presentation.demand.dto.DemandCreateRequestDto;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.BrandPayMethodFixture;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.ProductCatalogFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 4-1: Demand 동일 카탈로그 중복 생성")
class DemandCreateConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private DemandService demandService;
    @Autowired private DemandRepository demandRepository;
    @Autowired private MemberFixture memberFixture;
    @Autowired private ProductCatalogFixture productCatalogFixture;
    @Autowired private BrandPayMethodFixture brandPayMethodFixture;

    private Long memberId;
    private Long catalogId;
    private Long payMethodId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("동시등록유저").getId();
        catalogId = productCatalogFixture.save("사과", 5000).getId();
        payMethodId = brandPayMethodFixture.saveActive(memberId);
    }

    @Test
    @DisplayName("동일 회원·카탈로그로 50회 동시 create 시 1건만 성공하고 나머지는 DEMAND_ALREADY_EXISTS")
    void onlyOneSucceedsOnDuplicateCreate() throws Exception {
        int threadCount = 50;
        DemandCreateRequestDto request = new DemandCreateRequestDto(
            catalogId, payMethodId, 10000, 20000, 1, null, true,
            true, true, true, true
        );

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            try {
                demandService.create(request, memberId);
                return true;
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.DEMAND_ALREADY_EXISTS) throw e;
                return false;
            }
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(demandRepository.count()).isEqualTo(1L);
    }
}
