package com.ankur.design.hft.optimizingjvm;

/**
 * LESSON 3 — NullPointerException stack trace omission
 *
 * JVM flag: -XX:+OmitStackTraceInFastThrow (ON by default in HotSpot)
 *
 * When the JIT compiles a throw site and sees it fire many times,
 * it replaces the full exception allocation with a pre-allocated singleton:
 *
 *   BEFORE optimisation (first ~N throws):
 *     new NullPointerException()   ← allocates object on heap
 *     fills in stack trace          ← walks the call stack, expensive
 *     throws                        ← normal path
 *
 *   AFTER optimisation (after JIT kicks in):
 *     throw <cached singleton NPE>  ← no allocation, no stack walk
 *     e.getStackTrace() returns []  ← empty — no frames
 *     e.getMessage()    returns null
 *
 * WHY: stack trace filling (Throwable.fillInStackTrace) is expensive.
 * For hot throw sites the JVM trades debuggability for speed.
 *
 * PROOF STRATEGY:
 *   Throw NPE thousands of times in a loop.
 *   Print stack trace depth over time.
 *   After JIT kicks in, depth drops to 0 — the singleton has no trace.
 *
 * TO DISABLE:  -XX:-OmitStackTraceInFastThrow
 * TO REPRODUCE reliably: run with -server -XX:+TieredCompilation
 *
 * Practical impact for HFT:
 *   If you catch NPEs on a hot path (e.g., null order check), your exception
 *   handler will silently receive an NPE with no stack trace after warmup.
 *   This makes production debugging nearly impossible.
 *   FIX: use explicit null checks, not exception-driven control flow.
 */
public class OPT3_NpeOmitStackTrace {

    // force a NullPointerException
    static void causeNpe(String s) {
        s.length();    // throws NPE if s is null
    }

    public static void main(String[] args) {
        System.out.println("=== NullPointerException stack trace omission ===\n");
        System.out.println("Throwing NPE in a tight loop. Watch stack trace depth shrink.\n");
        System.out.printf("%-10s  %-12s  %s%n", "Iteration", "Stack depth", "Has message");
        System.out.println("-".repeat(45));

        int lastDepth = -1;
        for (int i = 0; i < 20_000; i++) {
            try {
                causeNpe(null);
            } catch (NullPointerException e) {
                int depth = e.getStackTrace().length;

                // only print when depth changes — shows the transition point
                if (depth != lastDepth) {
                    System.out.printf("%-10d  %-12d  %s%n",
                            i, depth,
                            e.getMessage() != null ? e.getMessage() : "<null — singleton NPE>");
                    lastDepth = depth;
                }
            }
        }

        System.out.println("\nOnce depth hits 0: JIT replaced allocation with a cached singleton.");
        System.out.println("To disable: add JVM flag -XX:-OmitStackTraceInFastThrow");

        // ── practical consequence ─────────────────────────────────────────────
        System.out.println("\n=== Practical fix: explicit null check, not exception flow ===");

        // BAD: using NPE as a signal
        String value = null;
        int lengthBad = -1;
        try {
            lengthBad = value.length();
        } catch (NullPointerException e) {
            // after JIT: e has no stack trace — where did this come from?
            lengthBad = 0;
        }

        // GOOD: explicit null guard — no exception, no JIT surprise
        int lengthGood = (value != null) ? value.length() : 0;

        System.out.printf("bad path length=%d  good path length=%d  (same result, different cost)%n",
                lengthBad, lengthGood);
    }
}