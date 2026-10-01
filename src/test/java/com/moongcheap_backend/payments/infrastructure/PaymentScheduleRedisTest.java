package com.moongcheap_backend.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import com.moongcheap_backend.payments.application.PaymentQueueProperties;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "PAYMENT_QUEUE_REDIS_HOST", matches = ".+")
class PaymentScheduleRedisTest {
    LettuceConnectionFactory connection;
    StringRedisTemplate redis;

    @BeforeEach void setUp() {
        int port = Integer.parseInt(System.getenv().getOrDefault("PAYMENT_QUEUE_REDIS_PORT", "6379"));
        connection = new LettuceConnectionFactory(System.getenv("PAYMENT_QUEUE_REDIS_HOST"), port);
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        redis.delete(PaymentSchedule.KEY);
    }

    @AfterEach void tearDown() {
        redis.delete(PaymentSchedule.KEY);
        connection.destroy();
    }

    @Test void 두_소비자가_동시에_요청해도_한_소비자만_후보를_받는다() throws Exception {
        PaymentQueueProperties properties = new PaymentQueueProperties();
        PaymentSchedule schedule = new PaymentSchedule(redis, properties);
        schedule.schedule(100L, LocalDateTime.now().minusSeconds(1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Optional<Long>>> results = List.of(
                executor.submit(() -> { start.await(); return schedule.claimDue(); }),
                executor.submit(() -> { start.await(); return schedule.claimDue(); }));
            start.countDown();
            assertThat(results).extracting(future -> future.get(5, TimeUnit.SECONDS).isPresent())
                .containsExactlyInAnyOrder(true, false);
            assertThat(schedule.score(100L)).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
