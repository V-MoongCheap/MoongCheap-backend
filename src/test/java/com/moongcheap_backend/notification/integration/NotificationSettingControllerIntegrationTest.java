package com.moongcheap_backend.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.member.infrastructure.MemberRepository;
import com.moongcheap_backend.notification.domain.NotificationType;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

@DisplayName("NotificationSettingController 통합 테스트")
class NotificationSettingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationSettingFixture notificationSettingFixture;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SessionTestHelper sessionTestHelper;

    private Cookie sessionCookie;
    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("noti-test").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Nested
    @DisplayName("GET /api/members/me/notification-settings")
    class List {

        @Test
        @DisplayName("opt-out이 일부 있는 회원의 설정을 조회하면 해당 타입만 enabled=false로 반환한다")
        void returnsSettingsWithOptOutsMarkedDisabled() throws Exception {
            notificationSettingFixture.optOut(memberId, NotificationType.DEMAND_REGISTERED);
            notificationSettingFixture.optOut(memberId, NotificationType.RECRUIT_STATUS);

            mockMvc.perform(get("/api/members/me/notification-settings")
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(NotificationType.values().length))
                .andExpect(jsonPath("$[?(@.type=='DEMAND_REGISTERED')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.type=='RECRUIT_STATUS')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.type=='BID_RESULT')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.type=='BID_RESULT')].mandatory").value(true))
                .andExpect(jsonPath("$[?(@.type=='BID_RESULT')].description").value("낙찰 결과"));
        }

        @Test
        @DisplayName("opt-out이 없는 회원의 설정을 조회하면 모든 타입의 enabled=true를 반환한다")
        void returnsAllEnabledWhenNoOptOuts() throws Exception {
            mockMvc.perform(get("/api/members/me/notification-settings")
                    .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(NotificationType.values().length))
                .andExpect(jsonPath("$[?(@.enabled==false)].length()").doesNotExist());
        }
    }

    @Nested
    @DisplayName("PATCH /api/members/me/notification-settings/{type}")
    class Edit {

        @Test
        @DisplayName("비필수 알림을 비활성화하면 NotificationOptOut이 저장된다")
        void savesOptOutWhenDisablingNonMandatoryNotification() throws Exception {
            mockMvc.perform(patch("/api/members/me/notification-settings/DEMAND_REGISTERED")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\": false}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(notificationSettingFixture.isOptedOut(memberId, NotificationType.DEMAND_REGISTERED))
                .isTrue();
        }

        @Test
        @DisplayName("비활성화된 비필수 알림을 활성화하면 NotificationOptOut이 삭제된다")
        void deletesOptOutWhenEnablingNonMandatoryNotification() throws Exception {
            notificationSettingFixture.optOut(memberId, NotificationType.DEMAND_REGISTERED);

            mockMvc.perform(patch("/api/members/me/notification-settings/DEMAND_REGISTERED")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\": true}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(notificationSettingFixture.isOptedOut(memberId, NotificationType.DEMAND_REGISTERED))
                .isFalse();
        }

        @Test
        @DisplayName("필수 알림을 활성화 요청은 허용된다")
        void allowsEnablingMandatoryNotification() throws Exception {
            mockMvc.perform(patch("/api/members/me/notification-settings/BID_RESULT")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\": true}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("이미 비활성화된 알림을 다시 비활성화하면 중복 저장되지 않는다")
        void doesNotDuplicateOptOutWhenAlreadyDisabled() throws Exception {
            notificationSettingFixture.optOut(memberId, NotificationType.DEMAND_REGISTERED);

            mockMvc.perform(patch("/api/members/me/notification-settings/DEMAND_REGISTERED")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\": false}")
                    .cookie(sessionCookie))
                .andExpect(status().isNoContent());

            assertThat(notificationSettingFixture.countOptOuts(memberId, NotificationType.DEMAND_REGISTERED))
                .isEqualTo(1L);
        }
    }
}
