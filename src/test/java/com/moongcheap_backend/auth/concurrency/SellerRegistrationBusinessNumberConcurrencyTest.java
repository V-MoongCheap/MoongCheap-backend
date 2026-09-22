package com.moongcheap_backend.auth.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("동시성 1-2: 동일 사업자번호로 서로 다른 회원이 동시 등록")
class SellerRegistrationBusinessNumberConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SessionTestHelper sessionTestHelper;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("N명의 서로 다른 회원이 같은 유효 사업자번호로 동시 register 시 1건만 성공, 나머지는 BUSINESS_NUMBER_DUPLICATED")
    void onlyOneMemberCanRegisterSameBusinessNumber() throws Exception {
        int threadCount = 50;

        List<Cookie> sessionCookies = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Long memberId = memberFixture.save("판매자_" + i).getId();
            sessionCookies.add(sessionTestHelper.loginAs(memberId));
        }

        String requestBody = """
            {
              "businessName": "문치프상회",
              "businessNumber": "123-45-67815",
              "mailOrderRegistrationNumber": "2024-서울강남-1234",
              "ownerName": "홍길동",
              "phoneNumber": "010-1234-5678"
            }
            """;

        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            MockHttpServletResponse response = mockMvc.perform(
                    post("/api/sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .cookie(sessionCookies.get(idx)))
                .andReturn()
                .getResponse();

            if (response.getStatus() == 200) {
                return true;
            }
            if (response.getStatus() == 409 && response.getContentAsString().contains("SELLER_003")) {
                return false;
            }
            throw new AssertionError(
                "예상치 못한 응답: status=" + response.getStatus()
                    + " body=" + response.getContentAsString());
        });

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);
        assertThat(sellerRepository.count()).isEqualTo(1L);
    }
}
