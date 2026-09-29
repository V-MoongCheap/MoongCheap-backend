package com.moongcheap_backend.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;

class LoadTestMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LoadTestMetrics metrics = new LoadTestMetrics(registry);

    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }

    @Test void onlyCountsCommittedOrdersAndOneBatchDelay() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        metrics.committed("order", "created", 1000, LocalDateTime.now().minusSeconds(3));
        assertThat(registry.get("moongcheap.completed").tags("stage", "order", "outcome", "created").counter().count()).isZero();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(registry.get("moongcheap.completed").tags("stage", "order", "outcome", "created").counter().count()).isEqualTo(1000);
        assertThat(registry.get("moongcheap.completion.delay").timer().count()).isEqualTo(1);
    }

    @Test void rollbackAndNonTransactionalCallsDoNotCount() {
        metrics.committed("payment", "succeeded", 1, null);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        metrics.committed("payment", "succeeded", 1, null);
        TransactionSynchronizationManager.getSynchronizations().forEach(s ->
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(registry.get("moongcheap.completed").tags("stage", "payment", "outcome", "succeeded").counter().count()).isZero();
    }

    @Test void duplicateDeliveryWithNoNewOrdersDoesNotRecordAnotherDelay() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        metrics.committed("order", "created", 0, LocalDateTime.now());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(registry.get("moongcheap.completed").tags("stage", "order", "outcome", "created").counter().count()).isZero();
        assertThat(registry.find("moongcheap.completion.delay").timer()).isNull();
    }
}
