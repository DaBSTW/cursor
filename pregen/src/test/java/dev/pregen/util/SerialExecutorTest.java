package dev.pregen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SerialExecutorTest {

    @Test
    void runsNestedSubmissionsWithoutRecursion() {
        List<Integer> order = new ArrayList<>();
        SerialExecutor executor = new SerialExecutor(Runnable::run, error -> {
            throw new AssertionError(error);
        });
        executor.execute(() -> {
            order.add(1);
            executor.execute(() -> order.add(3));
            order.add(2);
        });
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void neverRunsTwoTasksAtOnceOnAPool() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        SerialExecutor executor = new SerialExecutor(pool, error -> {
        });
        CountDownLatch done = new CountDownLatch(1000);
        for (int i = 0; i < 1000; i++) {
            int value = i;
            executor.execute(() -> {
                maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
                seen.add(value);
                concurrent.decrementAndGet();
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, maxConcurrent.get());
        assertEquals(1000, seen.size());
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, seen.get(i));
        }
    }

    @Test
    void keepsRunningAfterATaskThrows() {
        List<Throwable> errors = new ArrayList<>();
        List<String> ran = new ArrayList<>();
        SerialExecutor executor = new SerialExecutor(Runnable::run, errors::add);
        executor.execute(() -> {
            throw new IllegalStateException("boom");
        });
        executor.execute(() -> ran.add("after"));
        assertEquals(1, errors.size());
        assertEquals(List.of("after"), ran);
    }
}
