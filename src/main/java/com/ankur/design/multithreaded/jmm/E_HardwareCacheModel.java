package com.ankur.design.multithreaded.jmm;

/**
 * CONCEPT 5 — Hardware Cache Model and happens-before
 *
 * The JVM does not run on an abstract machine. It runs on real CPUs with caches.
 * Understanding this explains WHY volatile and synchronized are needed.
 *
 * Hardware layers (fastest → slowest):
 *
 *   CPU-1                           CPU-2
 *   ┌─────────────────────┐         ┌─────────────────────┐
 *   │ Registers (~0 ns)   │         │ Registers (~0 ns)   │
 *   │ L1 cache  (~1 ns)   │         │ L1 cache  (~1 ns)   │
 *   │ L2 cache  (~4 ns)   │         │ L2 cache  (~4 ns)   │
 *   └──────────┬──────────┘         └──────────┬──────────┘
 *              │                               │
 *         L3 cache (~10 ns, shared)            │
 *              └───────────────────────────────┘
 *                           │
 *                    RAM (~100 ns)
 *
 * What the JVM allows without synchronization:
 *   1. Reorder instructions (compiler + CPU out-of-order execution)
 *   2. Keep a variable in a register indefinitely — never flush to RAM
 *   3. Read from L1 cache — never check if RAM has a newer value
 *
 * happens-before (JMM rule):
 *   If action A happens-before action B, then A's effects are visible to B.
 *
 *   How to establish happens-before:
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │ Rule                    │ Establishes happens-before        │
 *   ├─────────────────────────┼────────────────────────────────── │
 *   │ volatile write/read     │ write HB any subsequent read      │
 *   │ synchronized exit/enter │ unlock HB any subsequent lock     │
 *   │ Thread.start()          │ all actions before start HB run() │
 *   │ Thread.join()           │ all of thread's actions HB join() │
 *   └─────────────────────────┴────────────────────────────────── ┘
 *
 * This example shows the classic "publication" problem:
 *   an object initialized by one thread may appear partially constructed
 *   to another thread without a happens-before edge.
 */
public class E_HardwareCacheModel {

    // ── BROKEN publication ─────────────────────────────────────────────────────
    // Writer initializes data field THEN sets ready=true.
    // Without volatile, the CPU/compiler is allowed to reorder these two writes.
    // Reader may see ready=true but data=0 — a partially published object.
    static int     data  = 0;
    static boolean ready = false;

    static void brokenPublication() throws InterruptedException {
        Thread writer = new Thread(() -> {
            data  = 42;         // (1) write data
            ready = true;       // (2) set flag — may be reordered BEFORE (1) by CPU
        });

        Thread reader = new Thread(() -> {
            while (!ready) { /* spin */ }
            // May print 0 even though writer set data=42 BEFORE ready=true in source code
            System.out.println("BROKEN data=" + data + "  (may be 0 due to reorder)");
        });

        writer.start(); reader.start();
        writer.join();
        reader.join(1000);
        if (reader.isAlive()) reader.interrupt();
    }

    // ── FIX: volatile on the flag ─────────────────────────────────────────────
    // volatile write to `ready` creates a happens-before edge.
    // Everything written BEFORE the volatile write is visible to
    // anyone who reads `ready` and sees true.
    static int             safeData  = 0;
    static volatile boolean safeReady = false;

    static void safePublication() throws InterruptedException {
        Thread writer = new Thread(() -> {
            safeData  = 42;         // (1) write data
            safeReady = true;       // (2) volatile write — acts as a memory fence
                                    //     guarantees (1) is visible before (2)
        });

        Thread reader = new Thread(() -> {
            while (!safeReady) { /* spin */ }   // volatile read — load-acquire
            // happens-before guarantees safeData=42 is visible here
            System.out.println("SAFE   data=" + safeData + "  (always 42)");
        });

        writer.start(); reader.start();
        writer.join();  reader.join(1000);
    }

    // ── FIX: Thread.start() establishes happens-before ────────────────────────
    // All actions in the parent thread before t.start() are visible to t's run().
    static void startHappensBefore() throws InterruptedException {
        int[] shared = {0};
        shared[0] = 99;                // write before start()

        Thread t = new Thread(() -> {
            // JMM guarantees: shared[0] == 99 here — start() is a HB edge
            System.out.println("start() HB: shared=" + shared[0] + "  (always 99)");
        });
        t.start();
        t.join();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- broken publication ---");
        brokenPublication();

        System.out.println("--- safe publication with volatile ---");
        safePublication();

        System.out.println("--- Thread.start() happens-before ---");
        startHappensBefore();
    }
}