package com.ankur.design.multithreaded.jmm;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * CONCEPT 6 — volatile vs synchronized: when to use which
 *
 * ┌────────────────────┬───────────────┬───────────────┐
 * │                    │   volatile    │  synchronized │
 * ├────────────────────┼───────────────┼───────────────┤
 * │ Visibility         │      ✓        │      ✓        │
 * │ Atomicity          │      ✗        │      ✓        │
 * │ Compound actions   │      ✗        │      ✓        │
 * │ Mutual exclusion   │      ✗        │      ✓        │
 * │ OS lock overhead   │      ✗        │  possible     │
 * └────────────────────┴───────────────┴───────────────┘
 *
 * Rule:
 *   volatile  → one writer, many readers (flag, status, published reference)
 *   synchronized / Atomic → multiple writers or read-modify-write
 */
public class F_VolatileVsSynchronized {

    // ── volatile is ENOUGH: simple flag, one writer ───────────────────────────
    static volatile boolean shutdownRequested = false;

    static void volatileFlag() throws InterruptedException {
        Thread worker = new Thread(() -> {
            while (!shutdownRequested) { /* do work */ }
            System.out.println("volatile flag: worker stopped cleanly");
        });
        worker.start();
        Thread.sleep(50);
        shutdownRequested = true;   // one writer, so volatile is sufficient
        worker.join(1000);
    }

    // ── volatile is NOT ENOUGH: read-modify-write ─────────────────────────────
    // x++ on a volatile is still 3 steps: read, add, write.
    // Two threads can interleave between the read and the write.
    static volatile int volatileCount = 0;

    static void volatileIncrement() throws InterruptedException {
        Runnable r = () -> { for (int i = 0; i < 100_000; i++) volatileCount++; };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("volatile count=" + volatileCount
                + "  expected=200,000  (will be less — volatile does NOT fix race)");
    }

    // ── synchronized IS ENOUGH: covers both visibility and atomicity ──────────
    static int syncCount = 0;

    static synchronized void syncIncrement() { syncCount++; }

    static void synchronizedIncrement() throws InterruptedException {
        Runnable r = () -> { for (int i = 0; i < 100_000; i++) syncIncrement(); };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("sync    count=" + syncCount + "  expected=200,000  ✓");
    }

    // ── AtomicInteger: best for single-variable counter ──────────────────────
    static AtomicInteger atomicCount = new AtomicInteger(0);

    static void atomicIncrement() throws InterruptedException {
        Runnable r = () -> { for (int i = 0; i < 100_000; i++) atomicCount.incrementAndGet(); };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("atomic  count=" + atomicCount + "  expected=200,000  ✓");
    }

    // ── volatile for safe publication of an immutable object ─────────────────
    // Pattern: write a fully constructed object, then publish the reference via volatile.
    // Reader sees either null or the complete object — never a half-constructed one.
    static volatile String config = null;   // reference published via volatile

    static void safeObjectPublication() throws InterruptedException {
        Thread writer = new Thread(() -> {
            String fullyBuilt = "host=db,port=5432";   // build first (immutable)
            config = fullyBuilt;                        // volatile write — memory fence
        });
        Thread reader = new Thread(() -> {
            while (config == null) { /* spin */ }
            System.out.println("published config: " + config + "  (always complete)");
        });
        writer.start(); reader.start();
        writer.join(); reader.join(1000);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- volatile flag (correct) ---");
        volatileFlag();

        System.out.println("--- volatile increment (WRONG) ---");
        volatileIncrement();

        System.out.println("--- synchronized increment (correct) ---");
        synchronizedIncrement();

        System.out.println("--- atomic increment (correct, fastest) ---");
        atomicIncrement();

        System.out.println("--- safe object publication via volatile ---");
        safeObjectPublication();
    }
}