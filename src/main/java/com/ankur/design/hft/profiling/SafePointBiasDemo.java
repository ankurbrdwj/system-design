package com.ankur.design.hft.profiling;

import java.util.Map;

/**
 * CONCEPT 2: Safe-Point Bias
 *
 * ReadMe: "Standard Java APIs for stack traces are safe-point biased —
 *          they only capture stack traces at JVM safe points, which may
 *          miss significant code sections, especially long-running native methods."
 *
 * A JVM safe point is a position in bytecode where:
 *   - The JVM can pause all threads (for GC, deoptimisation, etc.)
 *   - Stack traces can be captured reliably
 *
 * Safe points occur at:
 *   - Method calls
 *   - Loop back-edges (in interpreted/C1 JIT code)
 *   - Exception throws
 *
 * Safe points do NOT occur inside:
 *   - Tight counted loops (C2-compiled, loop-strip-mined away)
 *   - Native method calls
 *   - System.arraycopy(), Math.sqrt(), etc.
 *
 * CONSEQUENCE: Thread.getAllStackTraces() only fires at safe points.
 * If your hot code is a counted loop with no safe point, the sampling
 * profiler NEVER sees it and attributes zero cost to it.
 *
 * Async-profiler fixes this by using AsyncGetCallTrace (AGCT) which
 * fires via POSIX signal and captures the stack anywhere — even mid-loop.
 */
public class SafePointBiasDemo {

    // =========================================================================
    // Workload A — safe-point RICH (loop has a method call each iteration)
    //              Traditional sampler WILL see this.
    // =========================================================================
    static long safepointRichLoop(long iterations) {
        long sum = 0;
        for (long i = 0; i < iterations; i++) {
            sum += compute(i);   // method call = safe point every iteration
        }
        return sum;
    }

    static long compute(long x) { return x * x; }

    // =========================================================================
    // Workload B — safe-point POOR (tight counted loop, JIT elides safe points)
    //              Traditional sampler may MISS this entirely.
    //              Async-profiler's AGCT captures it via SIGPROF signal.
    // =========================================================================
    static long safepointPoorLoop(long iterations) {
        long sum = 0;
        // C2 JIT compiles this into a tight loop with no safe-point polling.
        // Thread.getAllStackTraces() cannot interrupt it mid-loop.
        for (long i = 0; i < iterations; i++) {
            sum += i * i;        // no method call — no safe point in C2 output
        }
        return sum;
    }

    // =========================================================================
    // Simulate what a safe-point-biased sampler sees
    // =========================================================================
    static void runBiasedSampler(Runnable workload, String label) throws InterruptedException {
        int[] samplesInWorkload = {0};
        int[] totalSamples = {0};
        boolean[] inWorkload = {false};

        // Background sampler — uses Thread.getAllStackTraces() (safe-point biased)
        Thread sampler = Thread.ofPlatform().daemon(true).name("biased-sampler").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
                for (Map.Entry<Thread, StackTraceElement[]> e : traces.entrySet()) {
                    if (e.getKey().getName().equals("workload-thread")) {
                        totalSamples[0]++;
                        // Check if any frame is our workload method
                        for (StackTraceElement frame : e.getValue()) {
                            if (frame.getMethodName().contains("Loop")) {
                                samplesInWorkload[0]++;
                                break;
                            }
                        }
                    }
                }
                try { Thread.sleep(1); } catch (InterruptedException ex) { break; }
            }
        });

        Thread worker = Thread.ofPlatform().name("workload-thread").start(workload);
        worker.join();
        sampler.interrupt();
        sampler.join(200);

        double pct = totalSamples[0] == 0 ? 0
                : 100.0 * samplesInWorkload[0] / totalSamples[0];

        System.out.printf("  %-25s  total samples=%4d  in-method=%4d  visible=%.0f%%%n",
                label, totalSamples[0], samplesInWorkload[0], pct);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("====================================================");
        System.out.println("  Safe-Point Bias Demo");
        System.out.println("====================================================");
        System.out.println("\nWarm up JIT (C2 needs ~10k iterations to kick in)...");

        // Warm up so C2 compiles both loops
        for (int i = 0; i < 3; i++) {
            safepointRichLoop(100_000);
            safepointPoorLoop(100_000);
        }

        System.out.println("\nRunning biased sampler on each workload:\n");

        // Safe-point rich: sampler should see it often
        runBiasedSampler(
                () -> safepointRichLoop(50_000_000L),
                "safepointRichLoop");

        // Safe-point poor: C2 elides safe points — sampler may miss it entirely
        runBiasedSampler(
                () -> safepointPoorLoop(50_000_000L),
                "safepointPoorLoop");

        System.out.println();
        System.out.println("Expected result:");
        System.out.println("  safepointRichLoop  → high visibility (method call each iteration)");
        System.out.println("  safepointPoorLoop  → low/zero visibility (tight loop, no safe point)");
        System.out.println();
        System.out.println("async-profiler fix:");
        System.out.println("  Uses SIGPROF signal → AsyncGetCallTrace (AGCT)");
        System.out.println("  Fires anywhere in the program — even mid counted-loop");
        System.out.println("  No safe-point dependency → sees safepointPoorLoop correctly");
        System.out.println();
        System.out.println("JVM flag to add safe points to every loop (for comparison):");
        System.out.println("  -XX:+UseCountedLoopSafepoints   (adds overhead but fixes bias)");
    }
}