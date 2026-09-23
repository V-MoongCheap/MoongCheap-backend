package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.moongcheap_backend.common.outbox.domain.*;
import com.moongcheap_backend.common.outbox.infrastructure.OutboxEventRepository;
import com.moongcheap_backend.payments.domain.Payments;
import com.moongcheap_backend.payments.infrastructure.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentOutboxPublishServiceUnitTest {
    @Test void 실행가능한_결제를_SortedSet에_발행하고_완료처리한다() {
        PaymentsRepository payments = mock(PaymentsRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        PaymentSchedule schedule = mock(PaymentSchedule.class);
        PaymentOutboxPublishService service = new PaymentOutboxPublishService(payments, outbox, schedule);
        Payments payment = mock(Payments.class);
        when(payment.isAutomaticallyExecutable()).thenReturn(true);
        when(payment.getId()).thenReturn(200L);
        OutboxEvent event = OutboxEvent.paymentScheduleSync(200L,
            LocalDateTime.now().plusMinutes(1), LocalDateTime.now().minusSeconds(1));
        ReflectionTestUtils.setField(event, "id", 300L);
        when(outbox.findById(300L)).thenReturn(Optional.of(event));
        when(outbox.findByIdForUpdate(300L)).thenReturn(Optional.of(event));
        when(payments.findByIdForUpdate(200L)).thenReturn(Optional.of(payment));

        service.publishOne(300L);

        verify(schedule).schedule(200L, event.getScheduledAt());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }
}
