package com.moongcheap_backend.notification.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.notification.application.NotificationSettingService;
import com.moongcheap_backend.notification.domain.NotificationOptOut;
import com.moongcheap_backend.notification.domain.NotificationType;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import com.moongcheap_backend.notification.presentation.dto.NotificationSettingResponseDto;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceSuccessTest {

    @Mock private NotificationOptOutRepository notificationOptOutRepository;

    @InjectMocks
    private NotificationSettingService service;

    @Nested
    @DisplayName("getAll - 성공")
    class GetAllTest {

        @Test
        void opt_out_설정이_있는_회원의_알림_설정_전체를_조회한다() {
            Long memberId = 1L;
            NotificationOptOut optOut = mock(NotificationOptOut.class);
            when(optOut.getType()).thenReturn(NotificationType.DEMAND_REGISTERED);
            when(notificationOptOutRepository.findAllByMemberId(memberId))
                .thenReturn(List.of(optOut));

            List<NotificationSettingResponseDto> result = service.getAll(memberId);

            assertThat(result).hasSize(NotificationType.values().length);
            NotificationSettingResponseDto demandRegistered = result.stream()
                .filter(dto -> dto.type() == NotificationType.DEMAND_REGISTERED)
                .findFirst().orElseThrow();
            assertThat(demandRegistered.enabled()).isFalse();
            assertThat(demandRegistered.description())
                .isEqualTo(NotificationType.DEMAND_REGISTERED.getDescription());
            assertThat(demandRegistered.mandatory())
                .isEqualTo(NotificationType.DEMAND_REGISTERED.isMandatory());

            assertThat(result.stream()
                .filter(dto -> dto.type() != NotificationType.DEMAND_REGISTERED)
                .allMatch(NotificationSettingResponseDto::enabled)).isTrue();
        }

        @Test
        void opt_out_설정이_없는_회원의_알림_설정_전체를_조회한다() {
            Long memberId = 1L;
            when(notificationOptOutRepository.findAllByMemberId(memberId))
                .thenReturn(List.of());

            List<NotificationSettingResponseDto> result = service.getAll(memberId);

            assertThat(result).hasSize(NotificationType.values().length);
            assertThat(result).allMatch(NotificationSettingResponseDto::enabled);
        }
    }

    @Nested
    @DisplayName("edit - 성공")
    class EditTest {

        @Test
        void 비필수_알림을_활성화한다() {
            Long memberId = 1L;
            NotificationType type = NotificationType.DEMAND_REGISTERED;

            service.edit(memberId, type, true);

            verify(notificationOptOutRepository).deleteByMemberIdAndType(memberId, type);
            verify(notificationOptOutRepository, never()).save(any(NotificationOptOut.class));
        }

        @Test
        void 비필수_알림을_비활성화하고_opt_out이_미존재한다() {
            Long memberId = 1L;
            NotificationType type = NotificationType.DEMAND_REGISTERED;
            when(notificationOptOutRepository.existsByMemberIdAndType(memberId, type))
                .thenReturn(false);

            service.edit(memberId, type, false);

            verify(notificationOptOutRepository).save(any(NotificationOptOut.class));
            verify(notificationOptOutRepository, never()).deleteByMemberIdAndType(memberId, type);
        }

        @Test
        void 비필수_알림을_비활성화하고_이미_opt_out이_존재한다() {
            Long memberId = 1L;
            NotificationType type = NotificationType.DEMAND_REGISTERED;
            when(notificationOptOutRepository.existsByMemberIdAndType(memberId, type))
                .thenReturn(true);

            service.edit(memberId, type, false);

            verify(notificationOptOutRepository, never()).save(any(NotificationOptOut.class));
        }

        @Test
        void 필수_알림을_활성화한다() {
            Long memberId = 1L;
            NotificationType type = Arrays.stream(NotificationType.values())
                .filter(NotificationType::isMandatory)
                .findFirst().orElseThrow();

            service.edit(memberId, type, true);

            verify(notificationOptOutRepository).deleteByMemberIdAndType(memberId, type);
        }
    }
}
