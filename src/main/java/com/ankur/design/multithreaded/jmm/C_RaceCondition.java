package com.ankur.design.multithreaded.jmm;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * CONCEPT 3 — Race Condition
 *
 * `count++` is NOT one operation. It compiles to three bytecode instructions:
 *
 *   ILOAD  count    // 1. read  current value from memory into CPU register
 *   IADD   1        // 2. add   1 in the register
 *   ISTORE count    // 3. write new value back to memory
 *
 * CPU/Cache picture:
 *
 *   Thread-1 (CPU-1)                Thread-2 (CPU-2)
 *   register = read(count) → 100   register = read(count) → 100   ← SAME stale value
 *   register = 100 + 1     → 101   register = 100 + 1     → 101
 *   write(count) = 101              write(count) = 101             ← one increment LOST
 *
 * Expected: 102. Actual: 101. One update was silently overwritten.
 *
 * Solutions shown:
 *   BROKEN    — plain int, no sync
 *   FIX-1     — synchronized block
 *   FIX-2     — AtomicInteger (CAS, no OS lock)
 */
public class C_RaceCondition {

    static final int THREADS    = 2;
    static final int INCREMENTS = 100_000;

    // ── BROKEN ────────────────────────────────────────────────────────────────
    static class BrokenCounter {
        int count = 0;
        void increment() { count++; }   // read-modify-write, not atomic
    }

    static void broken() throws InterruptedException {
        BrokenCounter c = new BrokenCounter();
        Thread t1 = new Thread(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        Thread t2 = new Thread(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("BROKEN     count=" + c.count + "  expected=" + (THREADS * INCREMENTS));
    }

    // ── FIX-1: synchronized ───────────────────────────────────────────────────
    // `synchronized` acquires the object's monitor — only one thread inside at a time.
    // Guarantees: atomicity (no interleave) + visibility (flush to main memory on exit).
    static class SynchronizedCounter {
        int count = 0;
        synchronized void increment() { count++; }   // whole method is one critical section
    }

    static void fixSynchronized() throws InterruptedException {
        SynchronizedCounter c = new SynchronizedCounter();
        Thread t1 = new Thread(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        Thread t2 = new Thread(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("SYNC       count=" + c.count + "  expected=" + (THREADS * INCREMENTS));
    }

    // ── FIX-2: AtomicInteger ──────────────────────────────────────────────────
    // Uses CPU-level CAS (Compare-And-Swap) instruction: LOCK XADD on x86.
    // No OS mutex, no thread park — faster than synchronized under high contention.
    static class AtomicCounter {
        AtomicInteger count = new AtomicInteger(0);
        void increment() { count.incrementAndGet(); }
    }

    static void fixAtomic() throws InterruptedException {
        AtomicCounter c = new AtomicCounter();
        Thread t1 = new Thread(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        Thread t2 = new Thread(() -> { for (int i = 0; i < INCREMENTS; i++) c.increment(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("ATOMIC     count=" + c.count + "  expected=" + (THREADS * INCREMENTS));
    }

    public static void main(String[] args) throws InterruptedException {
        broken();
        fixSynchronized();
        fixAtomic();
    }
}