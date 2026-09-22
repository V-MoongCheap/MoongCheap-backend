package com.moongcheap_backend.payments.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "moongcheap.payments.queue.enabled", havingValue = "true")
public class PaymentWorkerPool {
    private final PaymentWorker worker;
    private final ThreadPoolExecutor executor;
    private final Semaphore slots;
    private final int workers;
    private final PaymentQueueProperties properties;

    public PaymentWorkerPool(PaymentWorker worker, PaymentQueueProperties properties) {
        properties.validate();
        this.worker = worker;
        this.properties = properties;
        this.workers = properties.getWorkers();
        this.slots = new Semaphore(workers);
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> new Thread(task,
                "payment-worker-" + sequence.incrementAndGet()),
            new ThreadPoolExecutor.AbortPolicy());
    }

    @Scheduled(fixedDelayString = "${moongcheap.payments.queue.poll-delay-ms:1000}")
    public void poll() {
        if (executor.isShutdown()) return;
        for (int i = 0; i < workers && slots.tryAcquire(); i++) {
            try {
                executor.execute(() -> {
                    try { worker.runOne(); }
                    catch (RuntimeException exception) {
                        log.warn("Payment worker deferred an unfinished execution");
                    } finally { slots.release(); }
                });
            } catch (RejectedExecutionException exception) {
                slots.release();
            }
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(properties.getShutdownWait().toMillis(),
                TimeUnit.MILLISECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
