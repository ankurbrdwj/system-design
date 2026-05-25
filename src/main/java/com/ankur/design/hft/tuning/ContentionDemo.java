package com.ankur.design.hft.tuning;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

/**
 * TOPIC: Contention — when multiple threads compete for the same lock, they serialize.
 *
 * In HFT and low-latency systems, contention is one of the biggest killers of throughput.
 * A global lock forces all threads to queue up, destroying parallelism.
 *
 * Two examples shown:
 *  1. Shared counter: synchronized method vs LongAdder
 *  2. Shared StringBuilder logger: global lock vs ThreadLocal
 */
public class ContentionDemo {

    // -------------------------------------------------------------------------
    // EXAMPLE 1: Shared counter
    // -------------------------------------------------------------------------

    // BAD: synchronized method — every increment acquires the monitor lock.
    // Under N threads, only 1 thread runs at a time. All others park (OS context switch).
    // In HFT terms: this is a serialization point — throughput = 1 / (lock hold time).
    static class BadCounter {
        private long count = 0;

        // BAD: synchronized on 'this' — global lock, all threads contend on the same object.
        public synchronized void increment() {
            count++;  // only one thread can execute this at a time
        }

        public synchronized long get() {
            return count;
        }
    }

    // GOOD: LongAdder — internally stripes the counter across multiple "cells",
    // one per CPU core (based on thread identity hashing).
    // Each thread usually hits its own cell → no contention.
    // sum() combines all cells at read time — read is slightly heavier, write is much lighter.
    // LongAdder is the RIGHT tool for high-write, low-read counters (metrics, order counts).
    static class GoodCounter {
        private final LongAdder count = new LongAdder();

        // GOOD: no lock at all — each thread increments its own stripe cell
        public void increment() {
            count.increment();
        }

        public long get() {
            return count.sum();  // combines all stripes — slightly more work on read
        }
    }

    // -------------------------------------------------------------------------
    // EXAMPLE 2: Shared logger (StringBuilder)
    // -------------------------------------------------------------------------

    // BAD: A shared StringBuilder that every thread appends to, protected by a lock.
    // Every log call acquires the same lock. Under load, threads spend more time
    // waiting for the lock than doing actual work.
    static class BadLogger {
        private final StringBuilder buffer = new StringBuilder();
        private final Object lock = new Object();

        // BAD: all threads contend on 'lock' — only 1 thread logs at a time
        public void log(String message) {
            synchronized (lock) {
                buffer.append(message).append("\n");
            }
        }

        public String dump() {
            synchronized (lock) {
                return buffer.toString();
            }
        }
    }

    // GOOD: ThreadLocal StringBuilder — each thread has its own private buffer.
    // No sharing → no contention → no lock needed.
    // Pattern used in HFT logging: each thread writes to its own ring buffer or memory region.
    static class GoodLogger {
        // Each thread gets its own independent StringBuilder — zero contention
        private static final ThreadLocal<StringBuilder> threadBuffer =
            ThreadLocal.withInitial(StringBuilder::new);

        // GOOD: no lock — each thread accesses only its own buffer
        public void log(String message) {
            threadBuffer.get().append(message).append("\n");
        }

        public String dumpCurrentThread() {
            return threadBuffer.get().toString();
        }
    }

    // -------------------------------------------------------------------------
    // Benchmark helper
    // -------------------------------------------------------------------------

    private static long benchmarkCounter(int threadCount, int incrementsPerThread, boolean useBad)
            throws InterruptedException {
        BadCounter bad = new BadCounter();
        GoodCounter good = new GoodCounter();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < incrementsPerThread; i++) {
                        if (useBad) bad.increment();
                        else good.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        long startNs = System.nanoTime();
        start.countDown();
        done.await();
        long elapsed = System.nanoTime() - startNs;

        pool.shutdown();
        return elapsed;
    }

    public static void main(String[] args) throws InterruptedException {
        final int THREAD_COUNT = 8;
        final int INCREMENTS = 1_000_000;

        System.out.println("=== ContentionDemo ===");
        System.out.println();
        System.out.println("--- Example 1: Counter Contention ---");
        System.out.printf("Threads: %d, Increments per thread: %,d%n", THREAD_COUNT, INCREMENTS);
        System.out.println();

        // Warm up JIT
        benchmarkCounter(THREAD_COUNT, 10_000, true);
        benchmarkCounter(THREAD_COUNT, 10_000, false);

        // BAD: synchronized counter
        long badTime = benchmarkCounter(THREAD_COUNT, INCREMENTS, true);
        System.out.printf("BAD  (synchronized):  %,d ms  — all threads serialize on lock%n",
                badTime / 1_000_000);

        // GOOD: LongAdder
        long goodTime = benchmarkCounter(THREAD_COUNT, INCREMENTS, false);
        System.out.printf("GOOD (LongAdder):     %,d ms  — each thread hits its own stripe cell%n",
                goodTime / 1_000_000);

        System.out.printf("Speedup: %.1fx%n", (double) badTime / goodTime);

        System.out.println();
        System.out.println("--- Example 2: Logger Contention ---");

        BadLogger badLogger = new BadLogger();
        GoodLogger goodLogger = new GoodLogger();

        int LOG_COUNT = 100_000;

        // BAD: shared logger with lock
        long t0 = System.nanoTime();
        for (int i = 0; i < LOG_COUNT; i++) {
            badLogger.log("order-" + i);
        }
        long badLogTime = System.nanoTime() - t0;

        // GOOD: thread-local logger
        t0 = System.nanoTime();
        for (int i = 0; i < LOG_COUNT; i++) {
            goodLogger.log("order-" + i);
        }
        long goodLogTime = System.nanoTime() - t0;

        System.out.printf("BAD  (synchronized StringBuilder): %,d ms%n", badLogTime / 1_000_000);
        System.out.printf("GOOD (ThreadLocal StringBuilder):  %,d ms%n", goodLogTime / 1_000_000);

        System.out.println();
        System.out.println("Key insight: Lock contention serializes parallel threads.");
        System.out.println("  LongAdder  = stripe counter across CPU cells (JDK 8+).");
        System.out.println("  ThreadLocal = give each thread its own private state — zero sharing.");
        System.out.println("  In HFT: avoid ANY shared mutable state on the hot path.");
    }
}
