package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PaymentWorkerPoolUnitTest {
    @Test void 기본_한_슬롯만_실행하고_대기열을_쌓지_않는다() throws Exception {
        PaymentWorker worker = mock(PaymentWorker.class);
        PaymentQueueProperties properties = new PaymentQueueProperties();
        properties.setWorkers(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; })
            .when(worker).runOne();
        PaymentWorkerPool pool = new PaymentWorkerPool(worker, properties);
        try {
            pool.poll();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 5; i++) pool.poll();
            verify(worker, times(1)).runOne();
        } finally {
            release.countDown();
            pool.close();
        }
    }
}
