package com.ankur.design.multithreading.correctness;

import com.ankur.design.multithreaded.correctness.Counter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    @Test
    void oneMillionIncrements() throws InterruptedException {
        final int threads = 1000;
        final int incrementsPerThread = 1000;

        Counter counter = new Counter();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1_000_000L, counter.get());
    }
}