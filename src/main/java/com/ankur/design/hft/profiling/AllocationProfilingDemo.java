package com.ankur.design.hft.profiling;

import java.util.*;
import java.lang.management.*;

/**
 * CONCEPT 5: Allocation Profiling
 *
 * ReadMe: "Allocation Profiling: pinpointed inefficient string splitting
 *          causing excessive allocations and GC pressure."
 *
 * async-profiler allocation mode:
 *   asprof -e alloc -d 10 <pid>
 *
 * It samples object allocations on the TLAB slow path (when the
 * thread-local allocation buffer is exhausted and a new one must be
 * requested from the heap — the expensive path).
 *
 * This demo shows FOUR allocation anti-patterns that async-profiler catches,
 * each with a fix, and measures both allocation rate and GC impact.
 *
 *   A. String.split() — creates a regex Pattern + String[] + N String objects
 *   B. Autoboxing     — Integer/Long created for every primitive in a loop
 *   C. toString() in hot loop — hidden string allocation on every iteration
 *   D. Defensive copy — unnecessary cloning of arrays/collections
 */
public class AllocationProfilingDemo {

    // =========================================================================
    // A. String.split() — allocates Pattern + array + N strings per call
    // =========================================================================
    static long splitBad(String[] lines) {
        long sum = 0;
        for (String line : lines) {
            String[] parts = line.split(",");       // new Pattern + String[] each time
            for (String p : parts) sum += p.length();
        }
        return sum;
    }

    static long splitGood(String[] lines) {
        long sum = 0;
        for (String line : lines) {
            // indexOf + substring: no regex, no Pattern allocation
            int pos = 0, next;
            while ((next = line.indexOf(',', pos)) != -1) {
                sum += next - pos;
                pos = next + 1;
            }
            sum += line.length() - pos;
        }
        return sum;
    }

    // =========================================================================
    // B. Autoboxing — each primitive becomes a heap-allocated wrapper object
    // =========================================================================
    static long autoboxingBad(int[] values) {
        List<Integer> list = new ArrayList<>();
        for (int v : values) list.add(v);       // Integer.valueOf(v) on each: new Integer
        long sum = 0;
        for (Integer i : list) sum += i;         // unboxing: Integer.intValue()
        return sum;
    }

    static long autoboxingGood(int[] values) {
        // Primitive array: zero boxing, cache-friendly, no GC
        long sum = 0;
        for (int v : values) sum += v;
        return sum;
    }

    // =========================================================================
    // C. toString() in hot loop — StringBuilder created on every log check
    // =========================================================================
    static volatile boolean DEBUG_ENABLED = false; // simulates a logger flag

    static void toStringBad(int[] data) {
        for (int v : data) {
            // Even if DEBUG_ENABLED = false, the String is built before the check
            String msg = "Processing value: " + v + " result=" + (v * 2);  // allocates!
            if (DEBUG_ENABLED) System.out.println(msg);
        }
    }

    static void toStringGood(int[] data) {
        for (int v : data) {
            // Guard: only build the String if logging is actually enabled
            if (DEBUG_ENABLED) {
                System.out.println("Processing value: " + v + " result=" + (v * 2));
            }
        }
    }

    // =========================================================================
    // D. Defensive copy — unnecessary array clone on each method call
    // =========================================================================
    static double averageBad(int[] data) {
        int[] copy = Arrays.copyOf(data, data.length);  // defensive copy — wasteful
        long sum = 0;
        for (int v : copy) sum += v;
        return (double) sum / copy.length;
    }

    static double averageGood(int[] data) {
        long sum = 0;
        for (int v : data) sum += v;               // read-only — no copy needed
        return (double) sum / data.length;
    }

    // =========================================================================
    // GC pressure measurement
    // =========================================================================
    static long gcCollectionCount() {
        return ManagementFactory.getGarbageCollectorMXBeans()
                .stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    static long gcTimeMs() {
        return ManagementFactory.getGarbageCollectorMXBeans()
                .stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    static long benchmark(Runnable task, String label) {
        long gcBefore = gcCollectionCount();
        long start    = System.nanoTime();
        task.run();
        long elapsed = System.nanoTime() - start;
        long gcAfter  = gcCollectionCount();
        System.out.printf("  %-40s %,6d ms  GC events=%d%n",
                label, elapsed / 1_000_000, gcAfter - gcBefore);
        return elapsed;
    }

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  Allocation Profiling Demo");
        System.out.println("====================================================\n");

        // Prepare data
        int N = 500_000;
        String[] csvLines = new String[N];
        int[] intData     = new int[N];
        for (int i = 0; i < N; i++) {
            csvLines[i] = "field" + i + ",value" + i + ",extra" + i;
            intData[i]  = i;
        }

        // Warm up
        splitBad(csvLines); splitGood(csvLines);
        autoboxingBad(intData); autoboxingGood(intData);
        toStringBad(intData); toStringGood(intData);
        averageBad(intData); averageGood(intData);

        System.out.println("--- A. String.split() vs indexOf ---");
        long splitBadNs  = benchmark(() -> splitBad(csvLines),  "split() — regex+array+N strings");
        long splitGoodNs = benchmark(() -> splitGood(csvLines), "indexOf() — zero allocation");
        System.out.printf("  Speedup: %.1fx%n%n", (double) splitBadNs / splitGoodNs);

        System.out.println("--- B. Autoboxing vs primitive array ---");
        long boxBadNs  = benchmark(() -> autoboxingBad(intData),  "List<Integer> — Integer per element");
        long boxGoodNs = benchmark(() -> autoboxingGood(intData), "int[] — zero boxing");
        System.out.printf("  Speedup: %.1fx%n%n", (double) boxBadNs / boxGoodNs);

        System.out.println("--- C. toString() in hot loop ---");
        long strBadNs  = benchmark(() -> toStringBad(intData),  "toString every iter (DEBUG=false)");
        long strGoodNs = benchmark(() -> toStringGood(intData), "guarded toString");
        System.out.printf("  Speedup: %.1fx%n%n", (double) strBadNs / strGoodNs);

        System.out.println("--- D. Defensive copy vs direct read ---");
        long copyBadNs  = benchmark(() -> averageBad(intData),  "Arrays.copyOf() on every call");
        long copyGoodNs = benchmark(() -> averageGood(intData), "direct read, no copy");
        System.out.printf("  Speedup: %.1fx%n%n", (double) copyBadNs / copyGoodNs);

        System.out.println("What async-profiler -e alloc would show:");
        System.out.println("  A: splitBad  → Pattern, String[], String  (3+ allocs per call)");
        System.out.println("  B: autoboxBad → Integer  (1 alloc per element)");
        System.out.println("  C: toStringBad → char[], String  (2 allocs per loop)");
        System.out.println("  D: averageBad  → int[]  (1 large alloc per call)");
        System.out.println("\nAll four are invisible to CPU profiling but show clearly in alloc mode.");
    }
}