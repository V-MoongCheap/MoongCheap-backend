package com.moongcheap_backend.notification.application.failure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.notification.application.NotificationSettingService;
import com.moongcheap_backend.notification.domain.NotificationType;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceFailureTest {

    @Mock private NotificationOptOutRepository notificationOptOutRepository;

    @InjectMocks
    private NotificationSettingService service;

    @Nested
    @DisplayName("edit - 실패")
    class EditTest {

        @Test
        void 필수_알림을_비활성화한다() {
            Long memberId = 1L;
            NotificationType type = Arrays.stream(NotificationType.values())
                .filter(NotificationType::isMandatory)
                .findFirst().orElseThrow();

            assertThatThrownBy(() -> service.edit(memberId, type, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        }
    }
}
