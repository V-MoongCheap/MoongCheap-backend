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

@DisplayName("동시성 3-2: opt-out 저장/삭제 인터리브")
class NotificationOptOutInterleaveConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private NotificationOptOutRepository notificationOptOutRepository;
    @Autowired private MemberFixture memberFixture;
    @Autowired private SessionTestHelper sessionTestHelper;

    private Long memberId;
    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        dbCleaner.clearAll();
        memberId = memberFixture.save("인터리브유저").getId();
        sessionCookie = sessionTestHelper.loginAs(memberId);
    }

    @Test
    @DisplayName("동일 타입에 대해 enable/disable을 동시 인터리브 시 최종 상태는 0 또는 1건, PK 위반 없음")
    void interleavedEnableDisableIsIdempotent() throws Exception {
        int threadCount = 50; // 짝수여야 enable/disable이 균등하게 섞임
        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            boolean enabled = idx % 2 == 0;
            MockHttpServletResponse response = mockMvc.perform(
                    patch("/api/members/me/notification-settings/{type}", NotificationType.DEMAND_REGISTERED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": " + enabled + "}")
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

        assertThat(result.total()).isEqualTo(threadCount);
        long count = notificationOptOutRepository.findAllByMemberId(memberId).stream()
            .filter(o -> o.getType() == NotificationType.DEMAND_REGISTERED)
            .count();
        assertThat(count).isBetween(0L, 1L);
    }
}
