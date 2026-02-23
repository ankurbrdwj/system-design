package com.ankur.design.hft.orderbook;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 6: Cache Miss Profiling
 *
 * ReadMe: "Cache Miss Profiling: identified performance degradation due to
 *          cache misses when accessing large arrays."
 *          "Hardware Counters: profiles cache misses, branch misses, page
 *          faults, context switches using perf hardware counters."
 *
 * async-profiler hardware counter mode:
 *   asprof -e cache-misses -d 10 <pid>    (L3 cache miss sampling)
 *   asprof -e branches      -d 10 <pid>   (branch misprediction)
 *
 * Modern CPUs have 64-byte cache lines. When you access memory that is NOT
 * in the L1/L2/L3 cache, the CPU must fetch it from RAM (~200 cycles vs ~4).
 *
 * TWO access patterns on the same data:
 *
 *   SEQUENTIAL  — accesses array[0], array[1], array[2]...
 *     The hardware prefetcher recognises the stride and pre-fetches ahead.
 *     Almost all accesses hit L1/L2 cache → fast.
 *
 *   RANDOM      — accesses array[rand], array[rand], array[rand]...
 *     Prefetcher cannot predict the next address.
 *     Each access is a cache miss → RAM fetch → ~200 cycle stall.
 */
public class CacheMissDemo {

    // =========================================================================
    // False sharing — two threads write to variables on the SAME cache line.
    // Every write by Thread A invalidates Thread B's cached line (MESI protocol).
    // async-profiler -e cache-misses shows constant misses on these addresses.
    // =========================================================================
    static final class FalseSharing {
        volatile long counterA = 0;   // likely same 64-byte cache line as counterB
        volatile long counterB = 0;
    }

    static final class TrueSharing {
        // 8 bytes (counterA) + 56 bytes padding = 64-byte cache line boundary
        long p1, p2, p3, p4, p5, p6, p7;
        volatile long counterA = 0;
        long p8, p9, p10, p11, p12, p13, p14;
        volatile long counterB = 0;   // starts on a NEW 64-byte cache line
        long p15, p16, p17, p18, p19, p20, p21;
    }

    // =========================================================================
    // Main memory access patterns
    // =========================================================================
    static long sequentialAccess(long[] array) {
        long sum = 0;
        // Stride = 1 element = 8 bytes — hardware prefetcher pre-fetches 8 longs/line
        for (int i = 0; i < array.length; i++) sum += array[i];
        return sum;
    }

    static long randomAccess(long[] array, int[] indices) {
        long sum = 0;
        // Each access jumps to a random location — no prefetch possible
        for (int idx : indices) sum += array[idx];
        return sum;
    }

    static long stridedAccess(long[] array, int stride) {
        long sum = 0;
        // Stride = 8 (64 bytes) — skips exactly one full cache line each step
        // Every access is a cache miss (next element is in a different line)
        for (int i = 0; i < array.length; i += stride) sum += array[i];
        return sum;
    }

    // =========================================================================
    // Branch misprediction — unpredictable branches stall the CPU pipeline
    // =========================================================================
    static long branchPredictable(int[] data) {
        // Sorted data — branch is always true for the first half, false for second.
        // CPU branch predictor learns this quickly: ~0% misprediction.
        long sum = 0;
        for (int v : data) if (v < 50) sum += v;
        return sum;
    }

    static long branchUnpredictable(int[] data) {
        // Random data — branch alternates randomly, predictor cannot learn.
        // ~50% misprediction rate → ~15 cycle penalty per miss.
        long sum = 0;
        for (int v : data) if (v < 50) sum += v;
        return sum;
    }

    static long benchmark(Runnable task, String label, int warmup, int measured) {
        for (int i = 0; i < warmup; i++) task.run();
        long start = System.nanoTime();
        for (int i = 0; i < measured; i++) task.run();
        long ns = (System.nanoTime() - start) / measured;
        System.out.printf("  %-50s %,8d ns%n", label, ns);
        return ns;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("====================================================");
        System.out.println("  Cache Miss Demo");
        System.out.println("====================================================\n");

        // ---- 1. Sequential vs Random vs Strided access ----
        int SIZE = 16 * 1024 * 1024; // 16M longs = 128 MB (> L3 cache on most CPUs)
        long[] bigArray = new long[SIZE];
        for (int i = 0; i < SIZE; i++) bigArray[i] = i;

        int[] randomIndices = new int[SIZE];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < SIZE; i++) randomIndices[i] = rng.nextInt(SIZE);

        System.out.println("--- 1. Memory Access Patterns (128 MB array) ---");
        long seqNs    = benchmark(() -> sequentialAccess(bigArray), "Sequential (stride=1, prefetcher works)", 2, 5);
        long randNs   = benchmark(() -> randomAccess(bigArray, randomIndices), "Random (no prefetch, L3 miss every step)", 2, 5);
        long strideNs = benchmark(() -> stridedAccess(bigArray, 8), "Strided-8 (1 miss per cache line)", 2, 5);

        System.out.printf("%n  Random vs Sequential slowdown  : %.1fx%n", (double) randNs / seqNs);
        System.out.printf("  Strided vs Sequential slowdown : %.1fx%n%n", (double) strideNs / seqNs);

        // ---- 2. False sharing ----
        System.out.println("--- 2. False Sharing vs Cache-Line Padding ---");
        int ITERS = 50_000_000;

        FalseSharing fs  = new FalseSharing();
        TrueSharing  ts  = new TrueSharing();

        AtomicLong falseTime = new AtomicLong();
        AtomicLong trueTime  = new AtomicLong();

        // Two threads writing to adjacent variables simultaneously
        Thread fa = new Thread(() -> { for (int i=0;i<ITERS;i++) fs.counterA++; });
        Thread fb = new Thread(() -> { for (int i=0;i<ITERS;i++) fs.counterB++; });
        long start = System.nanoTime();
        fa.start(); fb.start(); fa.join(); fb.join();
        falseTime.set(System.nanoTime() - start);

        Thread ta = new Thread(() -> { for (int i=0;i<ITERS;i++) ts.counterA++; });
        Thread tb = new Thread(() -> { for (int i=0;i<ITERS;i++) ts.counterB++; });
        start = System.nanoTime();
        ta.start(); tb.start(); ta.join(); tb.join();
        trueTime.set(System.nanoTime() - start);

        System.out.printf("  False sharing (same cache line) : %,d ms%n", falseTime.get() / 1_000_000);
        System.out.printf("  Padded        (own cache line)  : %,d ms%n", trueTime.get() / 1_000_000);
        System.out.printf("  Padding speedup: %.1fx%n%n", (double) falseTime.get() / trueTime.get());

        // ---- 3. Branch misprediction ----
        System.out.println("--- 3. Branch Prediction ---");
        int[] sortedData = new int[1_000_000];
        int[] randomData = new int[1_000_000];
        for (int i = 0; i < sortedData.length; i++) {
            sortedData[i] = i % 100;             // sorted 0-99 repeating
            randomData[i] = rng.nextInt(100);    // random 0-99
        }
        java.util.Arrays.sort(sortedData);

        long predNs   = benchmark(() -> branchPredictable(sortedData),   "Sorted data  (predictable branch)", 3, 10);
        long unpredNs = benchmark(() -> branchUnpredictable(randomData), "Random data  (unpredictable branch)", 3, 10);
        System.out.printf("%n  Misprediction slowdown: %.1fx%n%n", (double) unpredNs / predNs);

        System.out.println("What async-profiler -e cache-misses would show:");
        System.out.println("  randomAccess()  → thick bar at sum += array[idx]");
        System.out.println("  FalseSharing    → thick bar at counterA++ and counterB++");
        System.out.println("  branchUnpred    → async-profiler -e branch-misses → bar at if (v<50)");
        System.out.println("\nFixes:");
        System.out.println("  Cache misses  → sequential/blocked access, NUMA-local allocation");
        System.out.println("  False sharing → 56-byte padding between hot variables");
        System.out.println("  Branch misses → sort data first, or use branchless arithmetic");
    }
}