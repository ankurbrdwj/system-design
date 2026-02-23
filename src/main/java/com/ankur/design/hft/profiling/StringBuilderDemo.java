package com.ankur.design.hft.profiling;

/**
 * CONCEPT 3: StringBuilder — Array Copying Hidden in Native Methods
 *
 * ReadMe: "StringBuilder Test: showed that traditional profilers misleadingly
 *          report most time in native methods; async-profiler correctly
 *          identifies costly array copying."
 *
 * When you append to a StringBuilder and the internal char[] buffer is full,
 * Java calls Arrays.copyOf() → System.arraycopy() — a NATIVE method.
 *
 * Traditional (safe-point biased) profiler:
 *   - Sees: "most time in System.arraycopy (native)" — unhelpful
 *   - Cannot tell which Java caller caused the copy
 *
 * Async-profiler with AGCT:
 *   - Shows the full call chain: yourMethod → append → ensureCapacity → arraycopy
 *   - You immediately know the fix: pre-size the StringBuilder
 *
 * This demo shows THREE strategies and measures the array-copy cost directly.
 */
public class StringBuilderDemo {

    static final int ITERATIONS = 100_000;
    static final String WORD    = "Hello, World! ";

    // =========================================================================
    // Strategy 1: No pre-sizing — triggers many array-copy expansions.
    //             Traditional profiler: "time in System.arraycopy" — blame native.
    //             Async-profiler:       "time in append→ensureCapacity" — blame Java.
    // =========================================================================
    static String noPrescaling() {
        StringBuilder sb = new StringBuilder(); // default capacity = 16
        for (int i = 0; i < ITERATIONS; i++) {
            sb.append(WORD);  // triggers grow+copy every ~doubling: 16→32→64→...
        }
        return sb.toString();
    }

    // =========================================================================
    // Strategy 2: Pre-sized — no array copies after construction.
    //             Async-profiler shows zero time in arraycopy for this path.
    // =========================================================================
    static String preSized() {
        int capacity = WORD.length() * ITERATIONS;
        StringBuilder sb = new StringBuilder(capacity); // exact capacity upfront
        for (int i = 0; i < ITERATIONS; i++) {
            sb.append(WORD);  // buffer never grows → zero arraycopy calls
        }
        return sb.toString();
    }

    // =========================================================================
    // Strategy 3: String concatenation (+) in a loop — worst case.
    //             Creates a new String object on EVERY iteration.
    //             javac compiles each + into new StringBuilder().append().toString()
    //             → ITERATIONS new StringBuilder allocations, ITERATIONS arraycopy calls.
    // =========================================================================
    @SuppressWarnings("StringConcatenationInLoop")
    static String stringConcat() {
        String result = "";
        for (int i = 0; i < 1_000; i++) { // fewer iterations — it's very slow
            result += WORD;               // hidden: new StringBuilder + copy each time
        }
        return result;
    }

    // =========================================================================
    // Count how many times ensureCapacityInternal would be called (buffer grows)
    // Formula: buffer doubles from 16 until it fits ITERATIONS * WORD.length() chars
    // =========================================================================
    static int countExpansions(int targetLength) {
        int capacity = 16;
        int expansions = 0;
        while (capacity < targetLength) {
            capacity = capacity * 2 + 2;  // StringBuilder growth formula
            expansions++;
        }
        return expansions;
    }

    static long benchmark(Runnable task, int warmupRuns, int measuredRuns) {
        for (int i = 0; i < warmupRuns; i++) task.run();
        long start = System.nanoTime();
        for (int i = 0; i < measuredRuns; i++) task.run();
        return (System.nanoTime() - start) / measuredRuns;
    }

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  StringBuilder — Array Copying Hidden in Native Methods");
        System.out.println("====================================================\n");

        int targetLen = WORD.length() * ITERATIONS;
        int expansions = countExpansions(targetLen);

        System.out.printf("Target string length : %,d chars%n", targetLen);
        System.out.printf("Buffer expansions    : %d (each triggers System.arraycopy)%n%n",
                expansions);

        // Benchmark each strategy
        long noPresizeNs  = benchmark(() -> noPrescaling(), 3, 10);
        long preSizedNs   = benchmark(() -> preSized(),     3, 10);
        long concatNs     = benchmark(() -> stringConcat(), 2, 5);

        System.out.println("=== Results ===");
        System.out.printf("  No pre-size  (+ arraycopy ×%d) : %,6d µs/call%n",
                expansions, noPresizeNs / 1000);
        System.out.printf("  Pre-sized    (0 arraycopy)      : %,6d µs/call%n",
                preSizedNs / 1000);
        System.out.printf("  String concat (1k iters)        : %,6d µs/call%n",
                concatNs / 1000);
        System.out.printf("%n  Pre-sizing speedup: %.1fx%n",
                (double) noPresizeNs / preSizedNs);

        System.out.println("\nWhat profilers show:");
        System.out.println("  Traditional profiler: \"50-80% time in System.arraycopy (native)\"");
        System.out.println("    → unhelpful — you don't know WHICH caller caused the copies");
        System.out.println();
        System.out.println("  Async-profiler flame graph:");
        System.out.println("    main → noPrescaling → append → ensureCapacityInternal → arraycopy");
        System.out.println("    → immediately obvious: pre-size the StringBuilder");
        System.out.println();
        System.out.println("Fix: new StringBuilder(WORD.length() * ITERATIONS)");
        System.out.println("     Or: String.join(\"\", Collections.nCopies(n, word))");
    }
}