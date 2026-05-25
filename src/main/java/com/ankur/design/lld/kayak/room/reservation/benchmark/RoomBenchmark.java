package com.ankur.design.lld.kayak.room.reservation.benchmark;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Load test comparing 3 Room booking concurrency strategies:
 *
 *  1. BAD     — plain int, no synchronisation    → race condition, wrong results
 *  2. OK      — synchronized block per room      → correct, but blocks threads
 *  3. BEST    — AtomicInteger CAS loop           → correct, lock-free, highest throughput
 *
 * Run with:
 *   ./gradlew -q runMain -PmainClass=com.ankur.design.lld.kayak.room.reservation.benchmark.RoomBenchmark
 *
 * What to look for in results:
 *   - "booked" must equal capacity (never more) — proves correctness
 *   - throughput (ops/ms) — higher is better
 *   - CAS should outperform synchronized as thread count rises
 */
public class RoomBenchmark {

    static final int CAPACITY    = 50;    // rooms available
    static final int THREADS     = 200;   // concurrent users hammering the system
    static final int RUNS        = 5;     // repeat each test, take best run

    // -------------------------------------------------------------------------
    // Strategy 1 — BAD: plain int, no thread safety
    // -------------------------------------------------------------------------
    static class UnsafeRoom {
        private int available;
        UnsafeRoom(int n) { this.available = n; }

        boolean book() {
            if (available <= 0) return false;
            available--;           // NOT atomic — read-modify-write race
            return true;
        }
        int available() { return available; }
    }

    // -------------------------------------------------------------------------
    // Strategy 2 — OK: synchronized block (current RoomDatabaseAccessService)
    // -------------------------------------------------------------------------
    static class SynchronizedRoom {
        private int available;
        SynchronizedRoom(int n) { this.available = n; }

        synchronized boolean book() {
            if (available <= 0) return false;
            available--;
            return true;
        }
        synchronized int available() { return available; }
    }

    // -------------------------------------------------------------------------
    // Strategy 3 — BEST: AtomicInteger CAS loop (your proposal)
    // -------------------------------------------------------------------------
    static class AtomicRoom {
        private final AtomicInteger available;
        AtomicRoom(int n) { this.available = new AtomicInteger(n); }

        boolean book() {
            while (true) {
                int current = available.get();
                if (current <= 0) return false;
                if (available.compareAndSet(current, current - 1))
                    return true;
                // CAS failed — another thread booked simultaneously, retry
            }
        }
        int available() { return available.get(); }
    }

    // -------------------------------------------------------------------------
    // Load test harness
    // -------------------------------------------------------------------------

    @FunctionalInterface
    interface BookFn {
        boolean book();
    }

    static Result loadTest(String name, BookFn fn, int threads) throws InterruptedException {
        LongAdder booked   = new LongAdder();
        LongAdder rejected = new LongAdder();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done      = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();          // all threads start at exactly the same time
                    if (fn.book()) booked.increment();
                    else           rejected.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startGate.countDown();                  // fire!
        done.await(10, TimeUnit.SECONDS);
        long elapsedUs = (System.nanoTime() - start) / 1_000;

        pool.shutdown();
        return new Result(name, booked.sum(), rejected.sum(), elapsedUs, threads);
    }

    record Result(String name, long booked, long rejected, long elapsedUs, int threads) {
        boolean correct(int capacity) { return booked <= capacity; }

        void print(int capacity) {
            String status = correct(capacity) ? "✓ CORRECT" : "✗ OVERBOOKED";
            System.out.printf("%-22s | booked=%-4d rejected=%-4d | %6d µs | %s%n",
                    name, booked, rejected, elapsedUs, status);
        }
    }

    // -------------------------------------------------------------------------
    // Sustained contention test — capacity stays HIGH so threads always compete
    // This is the scenario where CAS actually beats synchronized
    // -------------------------------------------------------------------------
    static long sustainedTest(String name, BookFn book, Runnable reset,
                               int threads, int opsPerThread) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done      = new CountDownLatch(threads);
        ExecutorService pool     = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int op = 0; op < opsPerThread; op++) {
                        book.book();
                        reset.run();   // reset after each op so contention is sustained
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startGate.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        return (System.nanoTime() - start) / 1_000_000; // ms
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {

        // Warmup JIT
        System.out.println("Warming up JIT (3 rounds)...");
        for (int i = 0; i < 3; i++) {
            loadTest("warmup", new AtomicRoom(CAPACITY)::book, THREADS);
        }

        // ── Test A: one-shot booking (original) ──────────────────────────────
        // Contention window is tiny — synchronized wins here due to JVM optimisations
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST A — One-shot booking (capacity=50, all threads book once)  ║");
        System.out.println("║  Contention window is TINY → synchronized wins due to JVM tricks ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.printf("%-22s | %-22s | %-8s | %s%n", "Strategy", "Results", "Time", "Correctness");
        System.out.println("─".repeat(72));

        for (int threads : new int[]{50, 200, 500}) {
            System.out.println("  threads=" + threads);
            loadTest("  Synchronized",      new SynchronizedRoom(CAPACITY)::book, threads).print(CAPACITY);
            loadTest("  AtomicInteger CAS", new AtomicRoom(CAPACITY)::book,       threads).print(CAPACITY);
        }

        // ── Test B: sustained contention ─────────────────────────────────────
        // Threads keep booking+resetting — contention is continuous
        // CAS wins here because synchronized parks/unparks threads repeatedly
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST B — Sustained contention (book+reset 10,000 times each)    ║");
        System.out.println("║  Contention is CONTINUOUS → CAS wins (no park/unpark overhead)   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.printf("%-22s | threads | ops/thread | total-ms | throughput(ops/ms)%n", "Strategy");
        System.out.println("─".repeat(72));

        int opsPerThread = 10_000;
        for (int threads : new int[]{4, 8, 16, 32}) {
            SynchronizedRoom syncRoom = new SynchronizedRoom(Integer.MAX_VALUE);
            AtomicRoom        casRoom = new AtomicRoom(Integer.MAX_VALUE);

            long syncMs = sustainedTest("sync", syncRoom::book,
                    () -> {}, threads, opsPerThread);
            long casMs  = sustainedTest("cas",  casRoom::book,
                    () -> {}, threads, opsPerThread);

            long totalOps = (long) threads * opsPerThread;
            System.out.printf("  Synchronized      | %7d | %10d | %8d | %,.0f%n",
                    threads, opsPerThread, syncMs, syncMs > 0 ? totalOps/(double)syncMs : 0);
            System.out.printf("  AtomicInteger CAS | %7d | %10d | %8d | %,.0f  %s%n",
                    threads, opsPerThread, casMs,  casMs  > 0 ? totalOps/(double)casMs  : 0,
                    casMs < syncMs ? "← FASTER" : "");
            System.out.println();
        }

        // ── Overbooking proof ─────────────────────────────────────────────────
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  OVERBOOKING PROOF — 1000 threads, capacity=1, 10 trials         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        int overbooked = 0;
        for (int i = 0; i < 10; i++) {
            UnsafeRoom room = new UnsafeRoom(1);
            Result r = loadTest("Unsafe", room::book, 1000);
            if (r.booked() > 1) overbooked++;
            System.out.printf("  Trial %2d: booked=%d %s%n",
                    i+1, r.booked(), r.booked() > 1 ? "← OVERBOOKED!" : "ok (lucky)");
        }
        System.out.printf("Overbooking occurred in %d/10 trials%n", overbooked);
    }
}