package com.moongcheap_backend.support.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 테스트용 러너.
 * 여러 스레드가 latch로 동시에 출발한 뒤, 성공/실패 카운트를 집계한다.
 * 태스크 안에서 실패 유형을 정확히 카운팅하려면 태스크가 예외를 삼키고 반환값을 활용하도록 작성한다.
 */
public final class ConcurrencyRunner {

    private ConcurrencyRunner() {}

    public record Result(int success, int failure) {

        public int total() {
            return success + failure;
        }
    }

    /** 태스크는 true를 반환하면 성공, false 또는 예외면 실패로 집계된다. */
    public static Result run(int threads, ConcurrentTask task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

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
                    failure.incrementAndGet();
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
        return new Result(success.get(), failure.get());
    }

    @FunctionalInterface
    public interface ConcurrentTask {

        boolean run(int index) throws Exception;
    }
}
