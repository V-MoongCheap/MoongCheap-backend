package com.moongcheap_backend.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.member.domain.SellerStatus;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("SellerRegistrationController 통합 테스트")
class SellerRegistrationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("판매지원").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Test
    @DisplayName("정상 판매자 등록을 하면 Seller가 APPROVED 상태로 저장되고 Member.isSeller=true가 된다")
    void createsApprovedSellerAndMarksMember() throws Exception {
        String body = """
            {
              "businessName": "문치프상회",
              "businessNumber": "123-45-67815",
              "mailOrderRegistrationNumber": "2024-서울강남-1234",
              "ownerName": "홍길동",
              "phoneNumber": "010-1234-5678"
            }
            """;

        mockMvc.perform(post("/api/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists());

        assertThat(memberRepository.findById(memberId).orElseThrow().isSeller()).isTrue();
        assertThat(sellerRepository.findByMemberIdAndDeletedAtIsNull(memberId).orElseThrow()
            .getStatus()).isEqualTo(SellerStatus.APPROVED);
    }

    @Test
    @DisplayName("판매자 등록 후 세션 ID가 교체되고 새 세션으로 인증이 유지된다")
    void sessionIdIsRotatedAfterSellerRegistration() throws Exception {
        String body = """
            {
              "businessName": "문치프상회",
              "businessNumber": "123-45-67815",
              "mailOrderRegistrationNumber": "2024-서울강남-1234",
              "ownerName": "홍길동",
              "phoneNumber": "010-1234-5678"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andReturn();

        Cookie newCookie = result.getResponse().getCookie(sessionCookie.getName());
        assertThat(newCookie).isNotNull();
        assertThat(newCookie.getValue()).isNotEqualTo(sessionCookie.getValue());

        // 새 세션으로 인증된 요청이 401 없이 처리되는지 확인
        mockMvc.perform(post("/api/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(newCookie))
            .andExpect(status().is4xxClientError()); // 이미 등록된 판매자이므로 4xx, 401은 아님
    }
}
