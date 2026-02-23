package com.ankur.design.hft.profiling;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;

/**
 * CONCEPT 1: Instrumenting Profilers vs Sampling Profilers
 *
 * ReadMe: "Instrumenting Profilers modify bytecode to record every method
 *          entry/exit — full trace, but high overhead and distortion."
 *          "Sampling Profilers take periodic snapshots — lightweight but
 *          limited by JVM safe points."
 *
 * This demo builds both approaches from scratch so you can see exactly
 * what each profiler type does under the hood, and why the overhead differs.
 *
 *  A. Manual Instrumentation — wrap every method call with a timer.
 *     This is what AspectJ, Byte Buddy, or a Java agent does at bytecode level.
 *
 *  B. Manual Sampling — a background thread periodically captures all
 *     thread stack traces. This is what async-profiler, JFR, and VisualVM do.
 */
public class InstrumentingVsSamplingDemo {

    // =========================================================================
    // A. INSTRUMENTATION APPROACH
    //    Record every method entry and exit — like a Java agent does.
    //    Overhead: two System.nanoTime() calls + map lookup per method call.
    // =========================================================================
    static final class InstrumentingProfiler {
        // method name → accumulated nanoseconds
        private final Map<String, Long> totalTime  = new LinkedHashMap<>();
        private final Map<String, Long> callCounts = new LinkedHashMap<>();
        private final Map<String, Long> enterTime  = new HashMap<>();

        /** Called at every method ENTRY (agent inserts this at bytecode level). */
        void onEnter(String method) {
            enterTime.put(method, System.nanoTime());
        }

        /** Called at every method EXIT (agent inserts this before every return). */
        void onExit(String method) {
            long elapsed = System.nanoTime() - enterTime.getOrDefault(method, 0L);
            totalTime.merge(method, elapsed, Long::sum);
            callCounts.merge(method, 1L, Long::sum);
        }

        void report() {
            System.out.println("\n=== Instrumenting Profiler Report ===");
            System.out.printf("  %-30s %10s %10s %12s%n",
                    "Method", "Calls", "Total ms", "Avg µs");
            System.out.println("  " + "-".repeat(66));
            totalTime.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> {
                        long calls = callCounts.getOrDefault(e.getKey(), 1L);
                        System.out.printf("  %-30s %10d %10.2f %12.2f%n",
                                e.getKey(),
                                calls,
                                e.getValue() / 1e6,
                                e.getValue() / 1e3 / calls);
                    });
        }
    }

    // =========================================================================
    // B. SAMPLING APPROACH
    //    A background thread wakes every N ms and snapshots all thread stacks.
    //    No method interception — it just observes where threads ARE at that moment.
    //    Cost: one ThreadMXBean.getAllStackTraces() call per interval.
    // =========================================================================
    static final class SamplingProfiler {
        private final Map<String, Integer> hitCount = new LinkedHashMap<>();
        private volatile boolean running = true;
        private int totalSamples = 0;
        private final ThreadMXBean tmx = ManagementFactory.getThreadMXBean();

        /** Start background sampling thread at the given interval. */
        Thread start(int intervalMs) {
            return Thread.ofPlatform().daemon(true).name("sampler").start(() -> {
                while (running) {
                    // Capture stack traces of ALL threads — what async-profiler does
                    Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
                    for (Map.Entry<Thread, StackTraceElement[]> e : traces.entrySet()) {
                        StackTraceElement[] stack = e.getValue();
                        if (stack.length > 0) {
                            // Record the TOP frame (what the thread is doing RIGHT NOW)
                            String frame = stack[0].getClassName() + "."
                                    + stack[0].getMethodName();
                            hitCount.merge(frame, 1, Integer::sum);
                            totalSamples++;
                        }
                    }
                    try { Thread.sleep(intervalMs); }
                    catch (InterruptedException ex) { break; }
                }
            });
        }

        void stop() { running = false; }

        void report() {
            System.out.println("\n=== Sampling Profiler Report ===");
            System.out.printf("  Total samples: %d%n%n", totalSamples);
            System.out.printf("  %-55s %8s %8s%n", "Method (top of stack)", "Hits", "CPU %");
            System.out.println("  " + "-".repeat(73));
            hitCount.entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("java.lang.Thread"))
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> System.out.printf("  %-55s %8d %7.1f%%%n",
                            e.getKey(), e.getValue(),
                            100.0 * e.getValue() / Math.max(totalSamples, 1)));
        }
    }

    // =========================================================================
    // Workload to profile — three methods with very different cost profiles
    // =========================================================================
    static void cheapMethod(InstrumentingProfiler ip) {
        ip.onEnter("cheapMethod");
        int x = 0;
        for (int i = 0; i < 1_000; i++) x += i;   // fast: ~1 µs
        ip.onExit("cheapMethod");
    }

    static void expensiveMethod(InstrumentingProfiler ip) {
        ip.onEnter("expensiveMethod");
        long sum = 0;
        for (long i = 0; i < 10_000_000L; i++) sum += i;  // slow: ~10 ms
        ip.onExit("expensiveMethod");
        if (sum == 0) System.out.print(""); // prevent DCE
    }

    static void mediumMethod(InstrumentingProfiler ip) {
        ip.onEnter("mediumMethod");
        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        ip.onExit("mediumMethod");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("====================================================");
        System.out.println("  Instrumenting vs Sampling Profiler Demo");
        System.out.println("====================================================");

        InstrumentingProfiler ip = new InstrumentingProfiler();
        SamplingProfiler       sp = new SamplingProfiler();

        sp.start(5); // sample every 5 ms

        // Run workload
        for (int i = 0; i < 100; i++) cheapMethod(ip);
        for (int i = 0; i < 5;   i++) expensiveMethod(ip);
        for (int i = 0; i < 20;  i++) mediumMethod(ip);

        sp.stop();
        Thread.sleep(20); // let sampler finish last batch

        ip.report();
        sp.report();

        System.out.println("\nKey difference:");
        System.out.println("  Instrumenting: knows EXACTLY how long every call took");
        System.out.println("  Sampling:      estimates WHERE time is spent (no interception)");
        System.out.println("  Overhead:      Instrumenting >> Sampling for production use");
    }
}