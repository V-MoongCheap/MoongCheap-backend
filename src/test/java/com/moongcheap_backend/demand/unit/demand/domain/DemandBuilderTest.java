package com.moongcheap_backend.demand.unit.demand.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Demand 빌더")
class DemandBuilderTest {

    @Test
    @DisplayName("builder()로 생성하면 demandBoardId가 없고 status는 UNASSIGNED이다")
    void defaultBuilder_createsUnassignedDemand() {
        LocalDateTime endAt = LocalDateTime.now().plusDays(1);

        Demand demand = Demand.builder()
            .memberId(1L)
            .catalogId(10L)
            .payMethodId(20L)
            .desiredPriceMin(10000)
            .desiredPriceMax(20000)
            .desireEndAt(endAt)
            .quantity(1)
            .extraRequirement("요청사항")
            .isSubstitutable(true)
            .build();

        assertThat(demand.getStatus()).isEqualTo(DemandStatus.UNASSIGNED);
        assertThat(demand.getDemandBoardId()).isNull();
        assertThat(demand.getMemberId()).isEqualTo(1L);
        assertThat(demand.getCatalogId()).isEqualTo(10L);
        assertThat(demand.getPayMethodId()).isEqualTo(20L);
        assertThat(demand.getDesiredPriceMin()).isEqualTo(10000);
        assertThat(demand.getDesiredPriceMax()).isEqualTo(20000);
        assertThat(demand.getDesireEndAt()).isEqualTo(endAt);
        assertThat(demand.getQuantity()).isEqualTo(1);
        assertThat(demand.getExtraRequirement()).isEqualTo("요청사항");
        assertThat(demand.isSubstitutable()).isTrue();
    }

    @Test
    @DisplayName("boardJoinBuilder()로 생성하면 demandBoardId가 세팅되고 status는 ASSIGNED이다")
    void boardJoinBuilder_createsAssignedDemand() {
        LocalDateTime endAt = LocalDateTime.now().plusDays(1);

        Demand demand = Demand.boardJoinBuilder()
            .memberId(1L)
            .catalogId(10L)
            .demandBoardId(100L)
            .payMethodId(20L)
            .desiredPriceMin(10000)
            .desiredPriceMax(20000)
            .desireEndAt(endAt)
            .quantity(2)
            .isSubstitutable(false)
            .extraRequirement("공동구매 참여")
            .build();

        assertThat(demand.getStatus()).isEqualTo(DemandStatus.ASSIGNED);
        assertThat(demand.getDemandBoardId()).isEqualTo(100L);
        assertThat(demand.getMemberId()).isEqualTo(1L);
        assertThat(demand.getCatalogId()).isEqualTo(10L);
        assertThat(demand.getPayMethodId()).isEqualTo(20L);
        assertThat(demand.getDesiredPriceMin()).isEqualTo(10000);
        assertThat(demand.getDesiredPriceMax()).isEqualTo(20000);
        assertThat(demand.getDesireEndAt()).isEqualTo(endAt);
        assertThat(demand.getQuantity()).isEqualTo(2);
        assertThat(demand.getExtraRequirement()).isEqualTo("공동구매 참여");
        assertThat(demand.isSubstitutable()).isFalse();
    }
}
