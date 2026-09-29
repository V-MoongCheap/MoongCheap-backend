package com.moongcheap_backend.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.moongcheap_backend.common.config.RestClientConfig;
import com.moongcheap_backend.payments.domain.enums.PaymentType;
import com.moongcheap_backend.payments.infrastructure.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

class PaymentWorkerTimeoutTest {
    @Test
    @Timeout(25)
    void 실제_읽기_타임아웃_후_오류를_처리하고_같은_워커로_다음_작업을_실행한다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server.createContext("/v1/brandpay/payments", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                received.countDown();
                // 응답도 연결 종료도 하지 않는다. 클라이언트의 실제 읽기 타임아웃으로 끝나야 한다.
                releaseServer.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var schedule = mock(PaymentSchedule.class);
        var execution = mock(PaymentExecutionService.class);
        var reconciliation = mock(PaymentReconciliationClient.class);
        var token = UUID.randomUUID();
        var request = new BrandPayPaymentClient.AutomaticPaymentRequest(
            "customer", "method", PaymentType.CARD, 1000, "order-1", "상품");
        when(schedule.claimDue()).thenReturn(Optional.of(1L), Optional.of(2L), Optional.empty());
        when(execution.begin(1L)).thenReturn(Optional.of(
            new PaymentExecutionService.Execution(1L, token, request, "same-key", false)));
        CountDownLatch nextStarted = new CountDownLatch(1);
        when(execution.begin(2L)).thenAnswer(invocation -> {
            nextStarted.countDown();
            return Optional.empty();
        });
        CountDownLatch errorHandled = new CountDownLatch(1);
        doAnswer(invocation -> { errorHandled.countDown(); return null; })
            .when(execution).handleError(eq(1L), eq(token), any(), eq(false));
        var client = new TossBrandPayPaymentClient(new RestClientConfig().restClient(),
            "http://127.0.0.1:" + server.getAddress().getPort(), "test-secret");
        var properties = new PaymentQueueProperties();
        properties.setWorkers(1);
        var pool = new PaymentWorkerPool(
            new PaymentWorker(schedule, execution, client, reconciliation), properties);
        server.start();
        try {
            long start = System.nanoTime();
            pool.poll();
            assertThat(received.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(errorHandled.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                .isBetween(4500L, 13000L);
            assertThat(releaseServer.getCount()).isEqualTo(1);
            var error = ArgumentCaptor.forClass(PaymentGatewayException.class);
            verify(execution).handleError(eq(1L), eq(token), error.capture(), eq(false));
            assertThat(error.getValue().kind()).isEqualTo(PaymentGatewayException.Kind.UNKNOWN);
            assertThat(error.getValue().code()).isEqualTo("GATEWAY_TRANSPORT_ERROR");
            verify(execution, never()).complete(anyLong(), any(), any());
            // 스케줄러처럼 다시 폴링하여 finally에서 슬롯이 반환되는지 검증한다.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (nextStarted.getCount() > 0 && System.nanoTime() < deadline) {
                pool.poll();
                nextStarted.await(10, TimeUnit.MILLISECONDS);
            }
            assertThat(nextStarted.getCount()).isZero();
        } finally {
            releaseServer.countDown();
            server.stop(0);
            pool.close();
        }
    }
}
