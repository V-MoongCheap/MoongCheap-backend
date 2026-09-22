package com.moongcheap_backend.support.concurrency;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 테스트용 러너.
 * 여러 스레드가 latch로 동시에 출발한 뒤, 성공/실패 카운트를 집계한다.
 * 태스크는 true(성공), false(예상된 실패)를 반환하거나, 예상 밖의 예외를 throw한다.
 * throw된 예외는 테스트 종료 후 AssertionError로 표면화된다.
 */
public final class ConcurrencyRunner {

    private ConcurrencyRunner() {}

    public record Result(int success, int failure) {

        public int total() {
            return success + failure;
        }
    }

    /** 태스크는 true를 반환하면 성공, false면 실패로 집계된다. 예외는 테스트 실패로 표면화된다. */
    public static Result run(int threads, ConcurrentTask task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.execute(() -> {
                try {
                    start.await();
                    if (task.run(idx)) {
                        success.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        if (!finished) {
            throw new IllegalStateException("동시성 태스크 타임아웃 (30s)");
        }
        if (!errors.isEmpty()) {
            AssertionError error = new AssertionError("동시성 태스크에서 예기치 않은 예외 발생");
            errors.forEach(error::addSuppressed);
            throw error;
        }
        return new Result(success.get(), failure.get());
    }

    @FunctionalInterface
    public interface ConcurrentTask {

        boolean run(int index) throws Exception;
    }
}
