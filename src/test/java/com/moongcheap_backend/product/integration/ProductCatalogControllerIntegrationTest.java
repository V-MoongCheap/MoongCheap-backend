package com.moongcheap_backend.product.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.product.domain.productCatalog.ProductCatalog;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.ProductCatalogFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("ProductCatalogController 통합 테스트")
class ProductCatalogControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private ProductCatalogFixture productCatalogFixture;
    @Autowired
    private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        Long memberId = memberFixture.save("도감유저").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Nested
    @DisplayName("GET /api/product-catalog")
    class GetHotProductCatalogs {

        @Test
        @DisplayName("10개 이상의 ProductCatalog가 있으면 상위 9개를 반환한다")
        void returnsTop9WhenMoreThanNine() throws Exception {
            for (int i = 0; i < 10; i++) {
                productCatalogFixture.save("도감" + i, 1000 * (i + 1));
            }

            mockMvc.perform(get("/api/product-catalog").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(9))
                .andExpect(jsonPath("$.totalCount").value(9))
                .andExpect(jsonPath("$.totalCount").value(9))
                .andExpect(jsonPath("$.list[0].name").value("도감9"))
                .andExpect(jsonPath("$.list[8].name").value("도감1"));
        }

        @Test
        @DisplayName("ProductCatalog가 5개면 존재하는 5개만 반환한다")
        void returnsAllWhenFewerThanNine() throws Exception {
            for (int i = 0; i < 5; i++) {
                productCatalogFixture.save("도감" + i, 1000);
            }

            mockMvc.perform(get("/api/product-catalog").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(5));
        }

        @Test
        @DisplayName("ProductCatalog가 없으면 빈 목록을 반환한다")
        void returnsEmptyWhenNoCatalogs() throws Exception {
            mockMvc.perform(get("/api/product-catalog").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/product-catalog/{id}")
    class GetById {

        @Test
        @DisplayName("id로 상품 도감 단건을 조회하면 상세 정보를 반환한다")
        void returnsDetailById() throws Exception {
            ProductCatalog catalog = productCatalogFixture.save("사과", 5000);

            mockMvc.perform(get("/api/product-catalog/" + catalog.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(catalog.getId()))
                .andExpect(jsonPath("$.name").value("사과"))
                .andExpect(jsonPath("$.listPrice").value(5000));
        }
    }
}
