package com.moongcheap_backend.member.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.ShippingAddress;
import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.member.infrastructure.ShippingAddressRepository;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import com.moongcheap_backend.support.integration.ShippingAddressFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("ShippingAddressController 통합 테스트")
class ShippingAddressControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MemberFixture memberFixture;
    @Autowired private ShippingAddressFixture shippingAddressFixture;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ShippingAddressRepository shippingAddressRepository;
    @Autowired private SessionTestHelper sessionTestHelper;
    @Autowired private EncryptionService encryptionService;

    private Cookie sessionCookie;
    private Long memberId;

    private static final String VALID_BODY = """
        {
          "alias": "집",
          "recipientName": "홍길동",
          "phoneNumber": "010-1234-5678",
          "zipcode": "06235",
          "address": "서울특별시 강남구 테헤란로 427",
          "addressDetail": "101동 202호",
          "entranceCode": "1234#",
          "requestMessage": "문 앞에",
          "setAsDefault": true
        }
        """;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("배송지유저").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Nested
    @DisplayName("GET /api/shipping-addresses")
    class List {

        @Test
        @DisplayName("배송지 목록을 조회하면 기본 배송지가 우선, 최근 등록순으로 반환된다")
        void returnsDefaultFirstThenRecent() throws Exception {
            shippingAddressFixture.save(memberId, "회사", false);
            shippingAddressFixture.save(memberId, "집", true);

            mockMvc.perform(get("/api/shipping-addresses").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].alias").value("집"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].phoneNumberMasked").value("010-****-5678"));
        }

        @Test
        @DisplayName("배송지가 없으면 빈 배열을 반환한다")
        void returnsEmptyWhenNone() throws Exception {
            mockMvc.perform(get("/api/shipping-addresses").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/shipping-addresses/{id}")
    class Detail {

        @Test
        @DisplayName("본인 소유 배송지 단건을 조회한다")
        void returnsOwnedAddress() throws Exception {
            ShippingAddress addr = shippingAddressFixture.save(memberId, "집", true);

            mockMvc.perform(get("/api/shipping-addresses/" + addr.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addr.getId()))
                .andExpect(jsonPath("$.alias").value("집"));
        }
    }

    @Nested
    @DisplayName("POST /api/shipping-addresses")
    class Create {

        @Test
        @DisplayName("첫 번째 배송지는 자동으로 기본으로 저장되고 전화번호가 암호화된다")
        void firstAddressBecomesDefault() throws Exception {
            mockMvc.perform(post("/api/shipping-addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

            java.util.List<ShippingAddress> saved =
                shippingAddressRepository.findAllByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId);
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).isDefault()).isTrue();
            assertThat(encryptionService.decrypt(saved.get(0).getPhoneNumber())).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("setAsDefault=true로 등록하면 기존 기본이 해제되고 새 항목이 기본이 된다")
        void unmarksOldDefaultWhenSetAsDefaultTrue() throws Exception {
            ShippingAddress old = shippingAddressFixture.save(memberId, "집", true);

            mockMvc.perform(post("/api/shipping-addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
                    .cookie(sessionCookie))
                .andExpect(status().isOk());

            ShippingAddress oldRefreshed = shippingAddressRepository.findById(old.getId()).orElseThrow();
            assertThat(oldRefreshed.isDefault()).isFalse();
        }

        @Test
        @DisplayName("4개 존재 상태에서 5번째 등록은 허용된다")
        void allowsFifthAddress() throws Exception {
            for (int i = 0; i < 4; i++) shippingAddressFixture.save(memberId, "addr" + i, i == 0);

            mockMvc.perform(post("/api/shipping-addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
                    .cookie(sessionCookie))
                .andExpect(status().isOk());

            assertThat(shippingAddressRepository.countByMemberId(memberId)).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("PATCH /api/shipping-addresses/{id}")
    class Edit {

        @Test
        @DisplayName("배송지를 수정하면 필드가 업데이트되고 새 전화번호가 암호화된다")
        void updatesFields() throws Exception {
            ShippingAddress addr = shippingAddressFixture.save(memberId, "집", true);

            String editBody = """
                {
                  "alias": "회사",
                  "recipientName": "홍길동",
                  "phoneNumber": "010-9999-8888",
                  "zipcode": "12345",
                  "address": "서울시 종로구",
                  "addressDetail": "5층",
                  "entranceCode": null,
                  "requestMessage": null
                }
                """;

            mockMvc.perform(patch("/api/shipping-addresses/" + addr.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(editBody)
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            ShippingAddress updated = shippingAddressRepository.findById(addr.getId()).orElseThrow();
            assertThat(updated.getAlias()).isEqualTo("회사");
            assertThat(encryptionService.decrypt(updated.getPhoneNumber())).isEqualTo("01099998888");
        }
    }

    @Nested
    @DisplayName("DELETE /api/shipping-addresses/{id}")
    class Delete {

        @Test
        @DisplayName("배송지를 삭제하면 물리 삭제되고 기본이었다면 다른 항목이 기본으로 승격된다")
        void promotesOldestWhenDefaultDeleted() throws Exception {
            ShippingAddress old = shippingAddressFixture.save(memberId, "가장오래된", false);
            ShippingAddress def = shippingAddressFixture.save(memberId, "기본", true);

            mockMvc.perform(delete("/api/shipping-addresses/" + def.getId())
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(shippingAddressRepository.findById(def.getId())).isEmpty();
            ShippingAddress remaining = shippingAddressRepository.findById(old.getId()).orElseThrow();
            assertThat(remaining.isDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("PATCH /api/shipping-addresses/{id}/default")
    class SetDefault {

        @Test
        @DisplayName("기본 아닌 배송지를 기본으로 지정하면 기존 기본이 해제된다")
        void switchesDefault() throws Exception {
            ShippingAddress old = shippingAddressFixture.save(memberId, "집", true);
            ShippingAddress newDef = shippingAddressFixture.save(memberId, "회사", false);

            mockMvc.perform(patch("/api/shipping-addresses/" + newDef.getId() + "/default")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(shippingAddressRepository.findById(old.getId()).orElseThrow().isDefault()).isFalse();
            assertThat(shippingAddressRepository.findById(newDef.getId()).orElseThrow().isDefault()).isTrue();
        }

        @Test
        @DisplayName("이미 기본 배송지를 다시 기본으로 지정하면 DB 변경 없이 성공한다")
        void noopWhenAlreadyDefault() throws Exception {
            ShippingAddress def = shippingAddressFixture.save(memberId, "집", true);

            mockMvc.perform(patch("/api/shipping-addresses/" + def.getId() + "/default")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(shippingAddressRepository.findById(def.getId()).orElseThrow().isDefault()).isTrue();
        }
    }
}
