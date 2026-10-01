package com.moongcheap_backend.demand.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.demand.domain.demand.DemandStatus;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoard;
import com.moongcheap_backend.demand.domain.demandBoard.DemandBoardStatus;
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

@DisplayName("DemandBoardController 통합 테스트")
class DemandBoardControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private ProductCatalogFixture productCatalogFixture;
    @Autowired private DemandFixture demandFixture;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long catalogId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        Long memberId = memberFixture.save("보드유저").getId();
        catalogId = productCatalogFixture.save("사과", 5000).getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Nested
    @DisplayName("GET /api/demand-boards/exists")
    class Exists {

        @Test
        @DisplayName("해당 catalog에 GB_GATHERING 상태 DemandBoard가 있으면 exists=true를 반환한다")
        void returnsTrueWhenBoardExists() throws Exception {
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 0,
                LocalDateTime.now().plusDays(3));

            mockMvc.perform(get("/api/demand-boards/exists")
                    .param("catalogId", catalogId.toString())
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("해당 catalog에 활성 DemandBoard가 없으면 exists=false를 반환한다")
        void returnsFalseWhenNoBoard() throws Exception {
            mockMvc.perform(get("/api/demand-boards/exists")
                    .param("catalogId", catalogId.toString())
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/demand-boards")
    class HotDemandBoard {

        @Test
        @DisplayName("GB_GATHERING 상태 DemandBoard 여러 건이 있으면 상위 3개 리스트를 반환한다")
        void returnsHotBoards() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 5, now.plusDays(1));
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 3, now.plusDays(2));
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 1, now.plusDays(3));
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 10, now.plusDays(4));

            mockMvc.perform(get("/api/demand-boards").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandBoards").isArray())
                .andExpect(jsonPath("$.demandBoards.length()").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/demand-boards/{demandBoardId}")
    class GetById {

        @Test
        @DisplayName("참여 중인 보드를 조회하면 isParticipating=true를 반환한다")
        void returnsBoardWithParticipating() throws Exception {
            Long uid = memberFixture.save("참여자").getId();
            Cookie userCookie = sessionTestHelper.loginAs(uid);
            DemandBoard board = demandFixture.saveBoard(catalogId,
                DemandBoardStatus.GB_GATHERING, 1, LocalDateTime.now().plusDays(2));
            demandFixture.saveWithStatus(uid, catalogId, board.getId(), DemandStatus.ASSIGNED);

            mockMvc.perform(get("/api/demand-boards/" + board.getId()).cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandBoardId").value(board.getId()))
                .andExpect(jsonPath("$.isParticipating").value(true));
        }

        @Test
        @DisplayName("참여하지 않은 보드를 조회하면 isParticipating=false를 반환한다")
        void returnsBoardWithoutParticipating() throws Exception {
            DemandBoard board = demandFixture.saveBoard(catalogId,
                DemandBoardStatus.GB_GATHERING, 0, LocalDateTime.now().plusDays(2));

            mockMvc.perform(get("/api/demand-boards/" + board.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandBoardId").value(board.getId()))
                .andExpect(jsonPath("$.isParticipating").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/demand-boards/catalog/{catalogId}")
    class GetByCatalogId {

        @Test
        @DisplayName("해당 catalog의 GB_GATHERING 보드 목록이 페이지네이션되어 반환된다")
        void returnsBoardsForCatalog() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 2, now.plusDays(1));
            demandFixture.saveBoard(catalogId, DemandBoardStatus.GB_GATHERING, 3, now.plusDays(2));

            mockMvc.perform(get("/api/demand-boards/catalog/" + catalogId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandBoards").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("minPrice/maxPrice로 필터링해 조회한다")
        void filtersByPriceRange() throws Exception {
            mockMvc.perform(get("/api/demand-boards/catalog/" + catalogId)
                    .param("minPrice", "10000")
                    .param("maxPrice", "50000")
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandBoards").isArray());
        }
    }
}
