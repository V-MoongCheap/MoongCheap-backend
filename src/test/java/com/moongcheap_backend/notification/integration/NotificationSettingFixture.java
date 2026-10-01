package com.moongcheap_backend.notification.integration;

import com.moongcheap_backend.notification.domain.NotificationOptOut;
import com.moongcheap_backend.notification.domain.NotificationType;
import com.moongcheap_backend.notification.infrastructure.NotificationOptOutRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationSettingFixture {

    @Autowired
    private NotificationOptOutRepository notificationOptOutRepository;

    public void optOut(Long memberId, NotificationType type) {
        notificationOptOutRepository.save(
            NotificationOptOut.builder().memberId(memberId).type(type).build()
        );
    }

    public boolean isOptedOut(Long memberId, NotificationType type) {
        return notificationOptOutRepository.existsByMemberIdAndType(memberId, type);
    }

    public long countOptOuts(Long memberId, NotificationType type) {
        return notificationOptOutRepository.findAllByMemberId(memberId).stream()
            .filter(o -> o.getType() == type)
            .count();
    }

    public void clear() {
        notificationOptOutRepository.deleteAllInBatch();
    }
}
