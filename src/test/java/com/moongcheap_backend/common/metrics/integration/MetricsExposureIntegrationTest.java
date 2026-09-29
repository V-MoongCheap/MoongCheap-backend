package com.moongcheap_backend.common.metrics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.moongcheap_backend.support.integration.AbstractIntegrationTest;
import com.moongcheap_backend.common.metrics.LoadTestMetrics;
import com.moongcheap_backend.payments.application.PaymentWorker;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MetricsExposureIntegrationTest extends AbstractIntegrationTest {
    @Autowired com.moongcheap_backend.groupbuy.application.GroupBuyPublicService groupBuyService;
    @Autowired PaymentWorker worker;
    @Autowired LoadTestMetrics metrics;
    @Autowired MeterRegistry registry;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void springProxyAndRealCommitAreExposedInPrometheus() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> groupBuyService.getOrderSource(-1L))
            .isInstanceOf(com.moongcheap_backend.common.exception.BusinessException.class);
        assertThat(registry.get("moongcheap.service.execution")
            .tags("service", groupBuyService.getClass().getSuperclass().getName(),
                "method", "getOrderSource", "outcome", "error").timer().count()).isPositive();
        worker.runOne();
        assertThat(registry.get("moongcheap.operation").tag("operation", "runOne").timer().count()).isPositive();
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> metrics.committed("order", "created", 3, null));
        double committed = registry.get("moongcheap.completed").tags("stage", "order", "outcome", "created").counter().count();
        tx.executeWithoutResult(status -> {
            metrics.committed("order", "created", 100, null);
            status.setRollbackOnly();
        });
        assertThat(registry.get("moongcheap.completed").tags("stage", "order", "outcome", "created").counter().count()).isEqualTo(committed);
        String body = mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("moongcheap_completed_total", "moongcheap_operation_seconds_bucket", "moongcheap_worker_active", "moongcheap_service_execution_seconds_bucket");
    }
}
