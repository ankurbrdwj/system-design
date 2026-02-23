package com.ankur.design.hft.profiling;

import java.util.*;
import java.lang.management.*;

/**
 * CONCEPT 9: Flame Graph Patterns + JVM Tuning Flags
 *
 * ReadMe: "Provides interactive flame graphs visualizing CPU usage,
 *          native calls, and kernel activity."
 *          "Tune JVM options (-XX:+PreserveFramePointer, debug info flags)
 *          for best profiling accuracy."
 *          "Combine CPU, lock, allocation, and native memory profiling
 *          for comprehensive diagnostics."
 *
 * A flame graph is a stack trace visualisation where:
 *   - X axis = time (wider bar = more samples = more CPU time)
 *   - Y axis = call depth (bottom = main, top = leaf method)
 *   - Colour = code type (green=Java, yellow=C++, red=native/kernel)
 *
 * This demo:
 *   A. Shows four common flame graph patterns (simulated as call stacks)
 *   B. Demonstrates the JVM flags that give async-profiler full visibility
 *   C. Shows a JMH-style harness (the right way to measure before profiling)
 */
public class FlameGraphPatternDemo {

    // =========================================================================
    // A. FLAME GRAPH PATTERNS — recognising what you see
    // =========================================================================

    /** Pattern 1: FAT TOP — one method consuming most of the CPU.
     *  Flame graph: wide flat bar at the top of a single call chain.
     *  Diagnosis:  this leaf method IS the bottleneck. */
    static long fatTopPattern(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += expensiveLeaf(i); // wide bar here in the flame graph
        }
        return sum;
    }
    static long expensiveLeaf(int x) {
        long r = x;
        for (int i = 0; i < 500; i++) r = r * 31 + i; // intentionally slow
        return r;
    }

    /** Pattern 2: TALL THIN TOWER — deep call chain, thin bars.
     *  Flame graph: narrow column going very high.
     *  Diagnosis:  deep recursion, usually not a bottleneck per frame
     *              but total stack depth may indicate design issue. */
    static long tallThinTower(int depth, int acc) {
        if (depth == 0) return acc;
        return tallThinTower(depth - 1, acc + depth); // deep recursion
    }

    /** Pattern 3: WIDE FLAT BASE — many short methods called at the same level.
     *  Flame graph: wide base, many different coloured segments.
     *  Diagnosis:  method-call overhead; consider inlining or batching. */
    static long wideFlatBase(int[] data) {
        long sum = 0;
        for (int v : data) {
            sum += step1(v);
            sum += step2(v);
            sum += step3(v);
            sum += step4(v);  // four small methods — four segments at same height
        }
        return sum;
    }
    static long step1(int v) { return v + 1; }
    static long step2(int v) { return v * 2; }
    static long step3(int v) { return v ^ 7; }
    static long step4(int v) { return v - 3; }

    /** Pattern 4: GC BAR — periodic wide bar from GC threads.
     *  Flame graph: horizontal stripe of GC activity interrupting your code.
     *  Diagnosis:  too many allocations; reduce allocation rate. */
    static void gcPressurePattern(int iterations) {
        List<byte[]> sink = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            sink.add(new byte[1024]);  // 1 KB each — forces frequent GC
            if (sink.size() > 1000) sink.subList(0, 500).clear(); // half-clear
        }
    }

    // =========================================================================
    // B. JVM FLAGS FOR BEST ASYNC-PROFILER ACCURACY
    // =========================================================================
    static void printJvmFlags() {
        System.out.println("=== JVM Flags for Best async-profiler Accuracy ===\n");
        String[][] flags = {
            {"-XX:+PreserveFramePointer",
             "Keeps frame pointer register (rbp) intact. Without this, native",
             "stack unwinding is impossible and the profiler sees truncated stacks."},

            {"-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints",
             "Generates debug info at non-safepoint locations.",
             "Without this, JIT-compiled frames are anonymous in the flame graph."},

            {"-XX:+UseCountedLoopSafepoints",
             "Adds safe points inside counted loops.",
             "Without this, sampling is biased away from tight loops (safe-point bias)."},

            {"-XX:FlightRecorderOptions=stackdepth=256",
             "Increases JFR stack depth (also helps async-profiler stacks).",
             "Default depth of 64 truncates deep call chains."},

            {"-Xss2m",
             "Larger thread stack for deep recursion profiling.",
             "Needed when profiling code with stack depth > default ~512 frames."},

            {"-XX:+AlwaysPreTouch",
             "Pre-faults all heap pages at JVM startup.",
             "Eliminates page-fault noise during profiling."},
        };

        for (String[] flag : flags) {
            System.out.printf("  Flag  : %s%n", flag[0]);
            System.out.printf("  Why   : %s%n", flag[1]);
            System.out.printf("  Detail: %s%n%n", flag[2]);
        }
    }

    // =========================================================================
    // C. JMH-STYLE HARNESS — measure before profiling
    //    Always benchmark first (find which method is slow),
    //    THEN attach async-profiler to confirm the root cause.
    // =========================================================================
    static void jmhStyleHarness() {
        System.out.println("=== JMH-style Benchmark Harness ===\n");

        int N = 1_000_000;
        int[] data = new int[N];
        Arrays.fill(data, 42);

        // ------ Warm-up phase (let JIT compile to C2) ------
        long sink = 0;
        System.out.print("  Warming up JIT");
        for (int warmup = 0; warmup < 5; warmup++) {
            sink += fatTopPattern(10_000);
            sink += wideFlatBase(data);
            sink += tallThinTower(100, 0);
            System.out.print(".");
        }
        System.out.println(" done (sink=" + sink + ")");

        // ------ Measured phase ------
        System.out.println("\n  Measured results:");
        int RUNS = 5;

        long fatNs = 0;
        for (int i = 0; i < RUNS; i++) fatNs += measure(() -> fatTopPattern(100_000));
        System.out.printf("  fatTopPattern       : %,6d µs%n", fatNs / RUNS / 1000);

        long wideNs = 0;
        for (int i = 0; i < RUNS; i++) wideNs += measure(() -> wideFlatBase(data));
        System.out.printf("  wideFlatBase        : %,6d µs%n", wideNs / RUNS / 1000);

        long towerNs = 0;
        for (int i = 0; i < RUNS; i++) towerNs += measure(() -> tallThinTower(5000, 0));
        System.out.printf("  tallThinTower(5000) : %,6d µs%n", towerNs / RUNS / 1000);

        long gcNs = 0;
        for (int i = 0; i < RUNS; i++) gcNs += measure(() -> gcPressurePattern(50_000));
        System.out.printf("  gcPressurePattern   : %,6d µs%n", gcNs / RUNS / 1000);

        // Check GC counts
        long gcCount = ManagementFactory.getGarbageCollectorMXBeans()
                .stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
        System.out.printf("%n  Total GC collections during benchmark: %d%n", gcCount);

        System.out.println("\n  Profiling next step:");
        System.out.printf("  Slowest method: %s%n",
                fatNs > gcNs ? "fatTopPattern" : "gcPressurePattern");
        System.out.println("  → attach async-profiler to that method's process");
        System.out.println("    asprof -e cpu -d 5 -f flamegraph.html <pid>");
    }

    static long measure(Runnable r) {
        long s = System.nanoTime(); r.run(); return System.nanoTime() - s;
    }

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  Flame Graph Patterns + JVM Profiling Tuning");
        System.out.println("====================================================\n");

        System.out.println("=== A. Flame Graph Patterns ===\n");
        System.out.println("  Pattern 1: FAT TOP");
        System.out.println("    Wide flat bar at top = expensiveLeaf() is the bottleneck");
        System.out.println("    Flame graph: main→fatTopPattern→expensiveLeaf [████████████████]");

        System.out.println("\n  Pattern 2: TALL THIN TOWER");
        System.out.println("    Deep narrow column = recursive call chain");
        System.out.println("    Flame graph: main→tallThinTower→tallThinTower→... [|]");

        System.out.println("\n  Pattern 3: WIDE FLAT BASE");
        System.out.println("    Many sibling bars = method-dispatch overhead");
        System.out.println("    Flame graph: main [step1|step2|step3|step4] at same height");

        System.out.println("\n  Pattern 4: GC BAR");
        System.out.println("    Horizontal stripe from GC threads = allocation pressure");
        System.out.println("    Flame graph: GC::do_collection [████] interrupting your code");
        System.out.println();

        printJvmFlags();
        jmhStyleHarness();

        System.out.println("\n=== async-profiler Command Reference ===");
        System.out.printf("  %-40s %s%n", "asprof -e cpu -d 10 -f cpu.html <pid>",       "CPU flame graph");
        System.out.printf("  %-40s %s%n", "asprof -e alloc -d 10 -f alloc.html <pid>",   "Allocation flame graph");
        System.out.printf("  %-40s %s%n", "asprof -e lock -d 10 -f lock.html <pid>",     "Lock contention");
        System.out.printf("  %-40s %s%n", "asprof -e wall -d 10 -f wall.html <pid>",     "Wall-clock (all threads)");
        System.out.printf("  %-40s %s%n", "asprof -e cache-misses -d 10 <pid>",          "L3 cache miss counter");
        System.out.printf("  %-40s %s%n", "asprof -e malloc -d 10 <pid>",                "Native malloc calls");
    }
}