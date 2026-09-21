package com.moongcheap_backend.demand.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.demand.domain.demand.Demand;
import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
import com.moongcheap_backend.demand.infrastructure.demand.DemandRepository;
import com.moongcheap_backend.demand.infrastructure.demandBoard.DemandBoardRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.DemandFixture;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.ProductCatalogFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("DemandController 통합 테스트")
class DemandControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private ProductCatalogFixture productCatalogFixture;
    @Autowired private DemandFixture demandFixture;
    @Autowired private DemandRepository demandRepository;
    @Autowired private DemandBoardRepository demandBoardRepository;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long memberId;
    private Long catalogId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("수요유저").getId();
        catalogId = productCatalogFixture.save("사과", 5000).getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Nested
    @DisplayName("GET /api/members/me/demand")
    class Read {

        @Test
        @DisplayName("파라미터 없이 조회하면 ACTIVE_STATUSES에 해당하는 수요만 반환한다")
        void returnsActiveOnlyByDefault() throws Exception {
            demandFixture.saveUnassigned(memberId, catalogId);

            mockMvc.perform(get("/api/members/me/demand").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demands.length()").value(1))
                .andExpect(jsonPath("$.demands[0].status").value("UNASSIGNED"));
        }

        @Test
        @DisplayName("특정 status로 필터링하면 지정한 status의 수요만 반환한다")
        void filtersByStatus() throws Exception {
            demandFixture.saveWithStatus(memberId, catalogId, null, DemandStatus.CANCELED);

            mockMvc.perform(get("/api/members/me/demand")
                    .param("statuses", "CANCELED")
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demands.length()").value(1))
                .andExpect(jsonPath("$.demands[0].status").value("CANCELED"));
        }
    }

    @Nested
    @DisplayName("GET /api/members/me/demand/{demandId}")
    class Get {

        @Test
        @DisplayName("본인 수요를 단건 조회하면 상세 정보가 반환된다")
        void returnsOwnedDemand() throws Exception {
            Demand d = demandFixture.saveUnassigned(memberId, catalogId);

            mockMvc.perform(get("/api/members/me/demand/" + d.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(d.getId()))
                .andExpect(jsonPath("$.status").value("UNASSIGNED"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/members/me/demand/{demandId}")
    class Cancel {

        @Test
        @DisplayName("UNASSIGNED 상태 수요를 취소하면 status=CANCELED가 된다")
        void cancelsUnassigned() throws Exception {
            Demand d = demandFixture.saveUnassigned(memberId, catalogId);

            mockMvc.perform(delete("/api/members/me/demand/" + d.getId()).cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(demandRepository.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(DemandStatus.CANCELED);
        }

        @Test
        @DisplayName("ASSIGNED 상태 수요를 취소하면 boardParticipantCount가 감소한다")
        void cancelsAssignedAndDecreasesBoardCount() throws Exception {
            DemandBoard board = demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING,
                5, LocalDateTime.now().plusDays(3));
            Demand d = demandFixture.saveWithStatus(memberId, catalogId, board.getId(),
                DemandStatus.ASSIGNED);

            mockMvc.perform(delete("/api/members/me/demand/" + d.getId()).cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(demandRepository.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(DemandStatus.CANCELED);
            assertThat(demandBoardRepository.findById(board.getId()).orElseThrow()
                .getParticipantCount()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("PATCH /api/members/me/demand/{demandId}/accept")
    class AcceptOffer {

        @Test
        @DisplayName("SUBSTITUTE_OFFERED 상태 수요의 오퍼를 승낙하면 ASSIGNED로 전이한다")
        void acceptsOffer() throws Exception {
            DemandBoard board = demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING,
                0, LocalDateTime.now().plusDays(3));
            Demand d = demandFixture.saveWithStatus(memberId, catalogId, board.getId(),
                DemandStatus.SUBSTITUTE_OFFERED);

            mockMvc.perform(patch("/api/members/me/demand/" + d.getId() + "/accept")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(demandRepository.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(DemandStatus.ASSIGNED);
        }
    }

    @Nested
    @DisplayName("PATCH /api/members/me/demand/{demandId}/reject")
    class RejectOffer {

        @Test
        @DisplayName("SUBSTITUTE_OFFERED 상태 수요의 오퍼를 거절하면 UNASSIGNED로 전이한다")
        void rejectsOffer() throws Exception {
            DemandBoard board = demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING,
                0, LocalDateTime.now().plusDays(3));
            Demand d = demandFixture.saveWithStatus(memberId, catalogId, board.getId(),
                DemandStatus.SUBSTITUTE_OFFERED);

            mockMvc.perform(patch("/api/members/me/demand/" + d.getId() + "/reject")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            Demand updated = demandRepository.findById(d.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(DemandStatus.UNASSIGNED);
            assertThat(updated.getDemandBoardId()).isNull();
        }
    }
}
