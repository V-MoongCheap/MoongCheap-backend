package com.moongcheap_backend.member.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.member.domain.SocialProvider;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.SellerRepository;
import com.moongcheap_backend.member.infrastructure.SocialCredentialRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SellerFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import com.moongcheap_backend.support.integration.SocialCredentialFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("ProfileController 통합 테스트")
class ProfileControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private SellerFixture sellerFixture;
    @Autowired private SocialCredentialFixture socialCredentialFixture;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private SocialCredentialRepository socialCredentialRepository;
    @Autowired private SessionTestHelper sessionTestHelper;
    @Autowired private EncryptionService encryptionService;

    private Cookie sessionCookie;
    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
    }

    @Nested
    @DisplayName("GET /api/members/me")
    class Detail {

        @Test
        @DisplayName("일반 회원 본인 프로필 조회 시 전화번호가 마스킹되고 sellerSummary는 null이다")
        void returnsGeneralMemberProfileWithMaskedPhone() throws Exception {
            memberId = memberFixture.savePlainPhone("일반유저", "01012345678").getId();
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(get("/api/members/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("일반유저"))
                .andExpect(jsonPath("$.phoneNumberMasked").value("010-****-5678"))
                .andExpect(jsonPath("$.isSeller").value(false))
                .andExpect(jsonPath("$.seller").doesNotExist());
        }

        @Test
        @DisplayName("판매자 회원 본인 프로필 조회 시 sellerSummary가 포함된다")
        void includesSellerSummaryForSellerMember() throws Exception {
            memberId = memberFixture.saveSeller("판매유저").getId();
            sellerFixture.saveApproved(memberId, "문치프상회", "1234567890");
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(get("/api/members/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSeller").value(true))
                .andExpect(jsonPath("$.seller.businessName").value("문치프상회"))
                .andExpect(jsonPath("$.seller.status").value("APPROVED"));
        }

        @Test
        @DisplayName("소셜 연동된 회원 프로필 조회 시 linkedProviders에 provider가 포함된다")
        void includesSocialProviders() throws Exception {
            memberId = memberFixture.save("소셜유저").getId();
            socialCredentialFixture.save(memberId, SocialProvider.KAKAO, "kakao-123");
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(get("/api/members/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedProviders[0]").value("KAKAO"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/members/me")
    class Edit {

        @Test
        @DisplayName("닉네임을 변경하면 member.nickname이 갱신된다")
        void updatesNickname() throws Exception {
            memberId = memberFixture.save("기존닉네임").getId();
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(patch("/api/members/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\": \"새닉네임\"}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            Member updated = memberRepository.findById(memberId).orElseThrow();
            assertThat(updated.getNickname()).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("전화번호를 변경하면 member.phoneNumber가 암호화되어 저장된다")
        void encryptsPhoneNumber() throws Exception {
            memberId = memberFixture.save("전화유저").getId();
            sessionCookie = sessionTestHelper.loginAs(memberId);

            mockMvc.perform(patch("/api/members/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\": \"010-9876-5432\"}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            Member updated = memberRepository.findById(memberId).orElseThrow();
            assertThat(updated.getPhoneNumber()).isNotNull();
            assertThat(encryptionService.decrypt(updated.getPhoneNumber())).isEqualTo("01098765432");
        }

        @Test
        @DisplayName("모든 필드가 null인 요청은 필드를 변경하지 않는다")
        void noChangesWhenAllFieldsNull() throws Exception {
            memberId = memberFixture.savePlainPhone("변경없음", "01012345678").getId();
            sessionCookie = sessionTestHelper.loginAs(memberId);
            String originalNickname = "변경없음";
            String originalPhone = memberRepository.findById(memberId).orElseThrow().getPhoneNumber();

            mockMvc.perform(patch("/api/members/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            Member updated = memberRepository.findById(memberId).orElseThrow();
            assertThat(updated.getNickname()).isEqualTo(originalNickname);
            assertThat(updated.getPhoneNumber()).isEqualTo(originalPhone);
        }
    }
}
