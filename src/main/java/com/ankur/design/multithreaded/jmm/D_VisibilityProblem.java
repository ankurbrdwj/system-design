package com.ankur.design.multithreaded.jmm;

/**
 * CONCEPT 4 — Visibility Problem
 *
 * Even if only ONE thread writes and ONE thread reads (no race on the write itself),
 * the reader may NEVER see the update because the JVM/CPU is allowed to:
 *   - keep the value in a CPU register (never write to RAM)
 *   - reorder instructions for performance
 *   - cache the value in L1/L2 and not flush it
 *
 * Hardware picture:
 *
 *   Writer Thread (CPU-1)         Reader Thread (CPU-2)
 *   ┌──────────────┐              ┌──────────────┐
 *   │ register=1   │              │ register=0   │  ← stale value from its own cache
 *   │      │       │              │              │
 *   │  L1 cache    │              │  L1 cache    │
 *   └──────────────┘              └──────────────┘
 *          │                             │
 *          └──────────── RAM ────────────┘
 *                    running = ?         ← write may not have reached RAM yet
 *
 * Without `volatile`, the reader may loop forever even after writer sets running=false.
 *
 * `volatile` fix:
 *   - Write to `volatile` → immediately flushed to main memory (store-release)
 *   - Read of `volatile`  → always fetched from main memory (load-acquire)
 *   - Establishes happens-before: writer's stores BEFORE the volatile write
 *     are visible to any thread that sees the volatile write
 *
 * Note: volatile guarantees VISIBILITY only, NOT atomicity.
 *   volatile int x; x++;  is still a race (read-modify-write, 3 steps).
 *   Use AtomicInteger for both visibility + atomicity.
 */
public class D_VisibilityProblem {

    // ── BROKEN — no visibility guarantee ─────────────────────────────────────
    static class BrokenFlag {
        boolean running = true;           // plain field — JVM may cache in register

        void stop()  { running = false; }
        void loop()  {
            long spins = 0;
            while (running) { spins++; }  // reader may never see running=false
            System.out.println("BROKEN loop exited after " + spins + " spins");
        }
    }

    // ── FIX: volatile ─────────────────────────────────────────────────────────
    static class VolatileFlag {
        volatile boolean running = true;  // volatile: all writes/reads go through main memory

        void stop()  { running = false; }
        void loop()  {
            long spins = 0;
            while (running) { spins++; }  // guaranteed to see the write from another thread
            System.out.println("VOLATILE loop exited after " + spins + " spins");
        }
    }

    // ── FIX: synchronized (also guarantees visibility) ────────────────────────
    // synchronized exit flushes all writes to main memory (happens-before)
    static class SynchronizedFlag {
        boolean running = true;

        synchronized void stop()         { running = false; }
        synchronized boolean isRunning() { return running; }

        void loop() {
            long spins = 0;
            while (isRunning()) { spins++; }
            System.out.println("SYNC loop exited after " + spins + " spins");
        }
    }

    static void demo(Runnable looper, Runnable stopper) throws InterruptedException {
        Thread reader = new Thread(looper,  "reader");
        Thread writer = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            stopper.run();
        }, "writer");
        reader.start();
        writer.start();
        writer.join();
        reader.join(2000);   // if reader is stuck (broken case), don't wait forever
        if (reader.isAlive()) {
            System.out.println("  *** reader is STUCK — never saw the update ***");
            reader.interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Note: BROKEN may or may not hang depending on JIT optimisation level.
        // Run with -server flag or after warmup to reliably reproduce the hang.
        BrokenFlag     bf = new BrokenFlag();
        VolatileFlag   vf = new VolatileFlag();
        SynchronizedFlag sf = new SynchronizedFlag();

        System.out.println("--- volatile fix ---");
        demo(vf::loop, vf::stop);

        System.out.println("--- synchronized fix ---");
        demo(sf::loop, sf::stop);

        System.out.println("--- broken (may hang on server JVM) ---");
        demo(bf::loop, bf::stop);
    }
}