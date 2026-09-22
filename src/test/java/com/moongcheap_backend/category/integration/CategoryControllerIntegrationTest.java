package com.moongcheap_backend.category.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.category.domain.Category;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("CategoryController 통합 테스트")
class CategoryControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryFixture categoryFixture;

    @Autowired
    private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        sessionCookie = sessionTestHelper.loginAs(1L);
    }

    @Nested
    @DisplayName("GET /api/categories")
    class GetCategories {

        @Test
        @DisplayName("depth=1로 최상위 카테고리 목록을 조회하면 parentId가 null인 항목만 반환한다")
        void returnsTopLevelCategoriesWhenDepthIsOne() throws Exception {
            Category top1 = categoryFixture.save(null, "식품", (short) 1);
            Category top2 = categoryFixture.save(null, "생활용품", (short) 1);
            categoryFixture.save(top1.getId(), "과자", (short) 2);

            mockMvc.perform(get("/api/categories")
                    .param("depth", "1")
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].parentId").doesNotExist())
                .andExpect(jsonPath("$[1].parentId").doesNotExist())
                .andExpect(jsonPath("$[0].depth").value(1))
                .andExpect(jsonPath("$[1].depth").value(1));
        }

        @Test
        @DisplayName("depth=2와 parentId로 하위 카테고리 목록을 조회하면 해당 부모의 자식만 반환한다")
        void returnsChildrenWhenDepthAndParentIdGiven() throws Exception {
            Category top1 = categoryFixture.save(null, "식품", (short) 1);
            Category top2 = categoryFixture.save(null, "생활용품", (short) 1);
            categoryFixture.save(top1.getId(), "과자", (short) 2);
            categoryFixture.save(top1.getId(), "음료", (short) 2);
            categoryFixture.save(top2.getId(), "세제", (short) 2);

            mockMvc.perform(get("/api/categories")
                    .param("depth", "2")
                    .param("parentId", top1.getId().toString())
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].parentId").value(top1.getId()))
                .andExpect(jsonPath("$[1].parentId").value(top1.getId()));
        }

        @Test
        @DisplayName("depth=3과 parentId로 3단계 카테고리 목록을 조회하면 해당 3단계 목록을 반환한다")
        void returnsGrandChildrenWhenDepthIsThree() throws Exception {
            Category top = categoryFixture.save(null, "식품", (short) 1);
            Category snack = categoryFixture.save(top.getId(), "과자", (short) 2);
            categoryFixture.save(snack.getId(), "초콜릿", (short) 3);
            categoryFixture.save(snack.getId(), "쿠키", (short) 3);

            mockMvc.perform(get("/api/categories")
                    .param("depth", "3")
                    .param("parentId", snack.getId().toString())
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].depth").value(3))
                .andExpect(jsonPath("$[1].depth").value(3));
        }

        @Test
        @DisplayName("결과가 없는 depth로 조회하면 빈 배열을 반환한다")
        void returnsEmptyArrayWhenNoMatches() throws Exception {
            mockMvc.perform(get("/api/categories")
                    .param("depth", "1")
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("depth=1이지만 parentId를 명시하면 findByDepthAndParentId 경로로 조회한다")
        void filtersByParentIdEvenAtDepthOne() throws Exception {
            Category parent = categoryFixture.save(null, "식품", (short) 1);
            Category withParent = categoryFixture.save(parent.getId(), "특이케이스", (short) 1);

            mockMvc.perform(get("/api/categories")
                    .param("depth", "1")
                    .param("parentId", parent.getId().toString())
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(withParent.getId()))
                .andExpect(jsonPath("$[0].parentId").value(parent.getId()));
        }
    }
}
