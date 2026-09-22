package com.moongcheap_backend.notification.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.moongcheap_backend.notification.domain.NotificationType;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import com.moongcheap_backend.support.integration.MemberFixture;
import com.moongcheap_backend.support.integration.SessionTestHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("동시성 3-1: 동일 회원 동일 타입 동시 opt-out")
class NotificationOptOutConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private NotificationOptOutRepository notificationOptOutRepository;
    @Autowired private MemberFixture memberFixture;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Long memberId;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("알림유저").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Test
    @DisplayName("동일 (memberId, type) 조합으로 50회 동시 opt-out 시 최종 레코드 1건, 모든 시도가 idempotent")
    void singleOptOutRecordUnderConcurrentDisable() throws Exception {
        int threadCount = 50;
        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            MockHttpServletResponse response = mockMvc.perform(
                    patch("/api/members/me/notification-settings/{type}", NotificationType.DEMAND_REGISTERED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}")
                        .cookie(sessionCookie))
                .andReturn()
                .getResponse();

            if (response.getStatus() == 204) {
                return true;
            }
            if (response.getStatus() == 409) {
                return false;
            }
            throw new AssertionError(
                "예상치 못한 응답: status=" + response.getStatus()
                    + " body=" + response.getContentAsString());
        });

        assertThat(result.success()).isGreaterThanOrEqualTo(1);
        long count = notificationOptOutRepository.findAllByMemberId(memberId).stream()
            .filter(o -> o.getType() == NotificationType.DEMAND_REGISTERED)
            .count();
        assertThat(count).isEqualTo(1L);
    }
}
