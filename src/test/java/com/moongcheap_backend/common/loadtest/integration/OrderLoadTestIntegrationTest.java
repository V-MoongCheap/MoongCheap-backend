package com.moongcheap_backend.common.loadtest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moongcheap_backend.common.loadtest.OrderLoadTestService;
import com.moongcheap_backend.groupbuy.application.GroupBuyOutboxPublishService;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyOrderCreationStream;
import com.moongcheap_backend.groupbuy.infrastructure.GroupBuyJudgmentSchedule;
import com.moongcheap_backend.order.application.GroupBuyOrderCreationConsumer;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

@TestPropertySource(properties = {
    "moongcheap.load-test.enabled=true",
    "moongcheap.payments.gateway.mode=mock",
    "moongcheap.payments.queue.worker-enabled=false",
    "moongcheap.security.internal-api-key-bypass=false"
})
class OrderLoadTestIntegrationTest extends AbstractIntegrationTest {
    @Autowired OrderLoadTestService service;
    @Autowired GroupBuyOutboxPublishService publisher;
    @Autowired GroupBuyOrderCreationConsumer consumer;
    @Autowired GroupBuyOrderCreationStream stream;
    @Autowired GroupBuyJudgmentSchedule judgmentSchedule;
    @Autowired JdbcTemplate jdbc;

    @Test void realOutboxStreamConsumerCreatesOneOrderPerDemandAndReplayDoesNotDuplicate() {
        var manifest = service.seed(2, 3);
        assertThat(service.verify(manifest).passed()).isFalse();
        for (var product : manifest.products()) {
            long groupId = service.create(manifest.runId(), product.productId());
            assertThat(service.create(manifest.runId(), product.productId())).isEqualTo(groupId);
        }
        publisher.publishBatch(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(1), 100);
        consumer.consume();
        var report = awaitPassed(manifest);
        assertThat(report.violations()).isEmpty();
        assertThat(report.passed()).isTrue();
        assertThat(report.actualOrders()).isEqualTo(6);

        long groupId = service.create(manifest.runId(), manifest.products().getFirst().productId());
        stream.publish(999999L, groupId);
        consumer.consume();
        assertThat(awaitPassed(manifest).passed()).isTrue();

        long demandId = manifest.products().getFirst().demands().getFirst().id();
        jdbc.update("update orders set total_amount=1 where demand_id=?", demandId);
        assertThat(service.verify(manifest).violations())
            .anyMatch(issue -> issue.kind().equals("ORDER_CONTENT") && issue.id() == demandId);
        jdbc.update("delete from orders where demand_id=?", demandId);
        assertThat(service.verify(manifest).violations())
            .anyMatch(issue -> issue.kind().equals("ORDER_COUNT") && issue.id() == demandId);
    }

    @Test void foreignRunCannotCreateGroupBuyAndInvalidSizeIsRejected() {
        var manifest = service.seed(1, 1);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), manifest.products().getFirst().productId()))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.seed(1000, 1000))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void cleanupRemovesOnlyTheOwnedRunFromDatabaseAndRedis() {
        var target = service.seed(1, 2);
        var untouched = service.seed(1, 1);
        long groupId = service.create(target.runId(), target.products().getFirst().productId());
        publisher.publishBatch(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(1), 100);
        consumer.consume();
        assertThat(judgmentSchedule.score(groupId)).isNotNull();

        var result = service.cleanup(target.runId());

        assertThat(result.products()).isEqualTo(1);
        assertThat(result.groupBuys()).isEqualTo(1);
        assertThat(result.orders()).isEqualTo(2);
        assertThat(result.demands()).isEqualTo(2);
        assertThat(judgmentSchedule.score(groupId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from product where description=?",
            Integer.class, "load-test-order:" + target.runId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from product where description=?",
            Integer.class, "load-test-order:" + untouched.runId())).isEqualTo(1);
        assertThatThrownBy(() -> service.cleanup(target.runId()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void bulkCleanupRemovesMultipleRunsAndPreservesUnrequestedRun() {
        var first = service.seed(1, 2);
        var second = service.seed(2, 1);
        var untouched = service.seed(1, 1);
        service.create(first.runId(), first.products().getFirst().productId());
        second.products().forEach(product -> service.create(second.runId(), product.productId()));
        publisher.publishBatch(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(1), 100);
        consumer.consume();

        var result = service.cleanupBulk(List.of(first.runId(), second.runId()));

        assertThat(result.runs()).isEqualTo(2);
        assertThat(result.products()).isEqualTo(3);
        assertThat(result.groupBuys()).isEqualTo(3);
        assertThat(result.orders()).isEqualTo(4);
        assertThat(result.demands()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from product where description in (?, ?)",
            Integer.class, "load-test-order:" + first.runId(),
            "load-test-order:" + second.runId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from product where description=?",
            Integer.class, "load-test-order:" + untouched.runId())).isEqualTo(1);
        assertThatThrownBy(() -> service.cleanupBulk(List.of(untouched.runId(), untouched.runId())))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.cleanupBulk(List.of(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class);
    }

    private OrderLoadTestService.Report awaitPassed(OrderLoadTestService.Manifest manifest) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        OrderLoadTestService.Report report;
        do {
            report = service.verify(manifest);
            if (report.passed()) return report;
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        } while (System.nanoTime() < deadline);
        return report;
    }

    @Test void endpointsRequireInternalKey() throws Exception {
        mockMvc.perform(post("/api/load-tests/internal/orders/seed")
            .contentType("application/json").content("{\"groups\":1,\"demandsPerGroup\":1}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/load-tests/internal/orders/seed")
            .header("X-Internal-Api-Key", "test-internal-api-key")
            .contentType("application/json").content("{\"groups\":1,\"demandsPerGroup\":1}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/load-tests/internal/orders/cleanup-bulk")
            .contentType("application/json").content("{\"runIds\":[]}"))
            .andExpect(status().isUnauthorized());
    }
}
