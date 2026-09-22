package com.moongcheap_backend.member.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.member.domain.Seller;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SellerFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SellerPublicController 통합 테스트")
class SellerPublicControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private SellerFixture sellerFixture;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("승인된 판매자의 공개 정보를 조회하면 사업자번호가 마스킹되어 반환된다")
    void returnsPublicInfoWithMaskedBusinessNumber() throws Exception {
        Long memberId = memberFixture.saveSeller("판매자").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
        Seller seller = sellerFixture.saveApproved(memberId, "문치프상회", "1234567890");

        mockMvc.perform(get("/api/sellers/" + seller.getId() + "/public")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("문치프상회"))
            .andExpect(jsonPath("$.ownerName").value("홍길동"))
            .andExpect(jsonPath("$.businessNumberMasked").value("123-45-67***"))
            .andExpect(jsonPath("$.mailOrderRegistrationNumber").value("2024-서울-1234"))
            .andExpect(jsonPath("$.phoneNumber").value("02-1234-5678"));
    }
}
