package com.moongcheap_backend.member.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("NicknameController 통합 테스트")
class NicknameControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Test
    @DisplayName("존재하지 않는 닉네임이면 available=true를 반환한다")
    void returnsAvailableTrueWhenNicknameNotTaken() throws Exception {
        mockMvc.perform(get("/api/members/nicknames/availability")
                .param("nickname", "문치프"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("문치프"))
            .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("존재하는 닉네임이면 available=false를 반환한다")
    void returnsAvailableFalseWhenNicknameTaken() throws Exception {
        memberFixture.save("중복");

        mockMvc.perform(get("/api/members/nicknames/availability")
                .param("nickname", "중복"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
    }
}
