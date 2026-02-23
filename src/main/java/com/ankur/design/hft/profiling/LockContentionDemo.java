package com.ankur.design.hft.profiling;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * CONCEPT 4: Lock Contention Profiling
 *
 * ReadMe: "Lock Contention Test: identified excessive lock contention on
 *          DatagramChannel sockets causing performance bottlenecks."
 *
 * Lock profiling mode in async-profiler:
 *   asprof -e lock -d 10 <pid>
 *
 * It measures time threads spend WAITING to acquire a lock — not the time
 * inside the lock. This is the contention cost, not the execution cost.
 *
 * This demo shows three patterns:
 *   A. HIGH CONTENTION  — single coarse lock shared by many threads
 *   B. REDUCED CONTENTION — lock striping (one lock per bucket)
 *   C. LOCK-FREE        — AtomicLong CAS (no lock at all)
 *
 * Async-profiler's lock profiling would show:
 *   A → deep red bar on the coarse lock's monitor
 *   B → smaller bars distributed across stripe locks
 *   C → no lock contention at all
 */
public class LockContentionDemo {

    static final int THREADS    = 8;
    static final int OPS_EACH   = 500_000;
    static final int STRIPES    = 16;

    // =========================================================================
    // A. HIGH CONTENTION — one lock for everything (what async-profiler finds)
    // =========================================================================
    static final class CoarseLockCounter {
        private long value = 0;
        private final Object lock = new Object();

        void increment() {
            synchronized (lock) { value++; }   // every thread blocks every other thread
        }
        long get() { return value; }
    }

    // =========================================================================
    // B. LOCK STRIPING — ConcurrentHashMap-style: one lock per stripe
    //    Threads hashing to different stripes never contend with each other.
    // =========================================================================
    static final class StripedCounter {
        private final long[]           counts = new long[STRIPES];
        private final ReentrantLock[]  locks  = new ReentrantLock[STRIPES];

        StripedCounter() {
            for (int i = 0; i < STRIPES; i++) locks[i] = new ReentrantLock();
        }

        void increment() {
            int stripe = (int)(Thread.currentThread().getId() % STRIPES);
            locks[stripe].lock();
            try { counts[stripe]++; }
            finally { locks[stripe].unlock(); }
        }

        long get() {
            long sum = 0;
            for (long c : counts) sum += c;
            return sum;
        }
    }

    // =========================================================================
    // C. LOCK-FREE — AtomicLong CAS (no blocking, no contention)
    // =========================================================================
    static final class LockFreeCounter {
        private final AtomicLong value = new AtomicLong(0);

        void increment() { value.incrementAndGet(); }   // LOCK XADD — no OS lock
        long get()       { return value.get(); }
    }

    // =========================================================================
    // Benchmark runner
    // =========================================================================
    static long run(Runnable[] tasks) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go    = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(tasks.length);
        Thread[] threads     = new Thread[tasks.length];

        for (int i = 0; i < tasks.length; i++) {
            Runnable task = tasks[i];
            threads[i] = new Thread(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                task.run();
                done.countDown();
            });
            threads[i].start();
        }

        ready.await();                  // all threads ready
        long start = System.nanoTime();
        go.countDown();                 // fire!
        done.await();
        return System.nanoTime() - start;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("====================================================");
        System.out.println("  Lock Contention Demo");
        System.out.println("====================================================\n");
        System.out.printf("Threads: %d   Ops each: %,d   Total ops: %,d%n%n",
                THREADS, OPS_EACH, (long) THREADS * OPS_EACH);

        // ---- A. Coarse lock ----
        CoarseLockCounter coarse = new CoarseLockCounter();
        Runnable[] coarseTasks = new Runnable[THREADS];
        for (int i = 0; i < THREADS; i++)
            coarseTasks[i] = () -> { for (int j = 0; j < OPS_EACH; j++) coarse.increment(); };

        long coarseNs = run(coarseTasks);

        // ---- B. Striped lock ----
        StripedCounter striped = new StripedCounter();
        Runnable[] stripedTasks = new Runnable[THREADS];
        for (int i = 0; i < THREADS; i++)
            stripedTasks[i] = () -> { for (int j = 0; j < OPS_EACH; j++) striped.increment(); };

        long stripedNs = run(stripedTasks);

        // ---- C. Lock-free ----
        LockFreeCounter lockFree = new LockFreeCounter();
        Runnable[] lockFreeTasks = new Runnable[THREADS];
        for (int i = 0; i < THREADS; i++)
            lockFreeTasks[i] = () -> { for (int j = 0; j < OPS_EACH; j++) lockFree.increment(); };

        long lockFreeNs = run(lockFreeTasks);

        long expected = (long) THREADS * OPS_EACH;
        System.out.println("=== Results ===");
        System.out.printf("  Coarse lock  : %,6d ms  (correct=%b)%n",
                coarseNs / 1_000_000, coarse.get() == expected);
        System.out.printf("  Striped lock : %,6d ms  (correct=%b) — %.1fx faster%n",
                stripedNs / 1_000_000, striped.get() == expected,
                (double) coarseNs / stripedNs);
        System.out.printf("  Lock-free    : %,6d ms  (correct=%b) — %.1fx faster%n",
                lockFreeNs / 1_000_000, lockFree.get() == expected,
                (double) coarseNs / lockFreeNs);

        System.out.println("\nWhat async-profiler -e lock would show:");
        System.out.println("  Coarse lock  → thick red bar: threads 1-7 all blocking on 'lock'");
        System.out.println("  Striped lock → 16 thin bars: each stripe has 0.5 threads on average");
        System.out.println("  Lock-free    → no lock bars at all");
        System.out.println("\nFix pattern: replace synchronized(singleLock) with:");
        System.out.println("  • Lock striping (as above)");
        System.out.println("  • ConcurrentHashMap / LongAdder for counters");
        System.out.println("  • AtomicLong / AtomicReference for single variables");
        System.out.println("  • LMAX Disruptor / ring buffer for producer-consumer");
    }
}