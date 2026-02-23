package com.ankur.design.hft.orderbook;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gcc/gdb/valgrind → Java Profiling Tools
 *
 * From the README:
 *   valgrind --tool=callgrind   → async-profiler / JProfiler  (CPU flame graphs)
 *   valgrind --tool=helgrind    → ThreadSanitizer / VisualVM  (data race detection)
 *   perf stat (hw counters)     → JMH + async-profiler -e cpu-cycles
 *   gdb                         → IntelliJ debugger + jstack + jmap
 *
 * This demo shows the PURE JAVA equivalents (no external tools required):
 *   A. JMH-style micro-benchmark harness (manual warm-up + measurement loop)
 *   B. Thread dump via Thread.getAllStackTraces() (what jstack does)
 *   C. Heap analysis via MemoryMXBean (what jmap -histo shows)
 *   D. GC monitoring via GarbageCollectorMXBean (detect GC pauses inline)
 *   E. Data-race detection pattern using volatile + happens-before assertions
 */
public class JavaProfilingToolsDemo {

    // =========================================================================
    // A. JMH-style Micro-benchmark Harness
    //    JMH (Java Microbenchmark Harness) is the tool; this shows the pattern.
    //    Key rules: warm up the JIT first, prevent dead-code elimination via checksum.
    // =========================================================================
    static void jmhStyleBenchmark() {
        System.out.println("--- A. JMH-style Micro-benchmark ---");

        // Subject under test: HashMap vs TreeMap lookup
        Map<String, Double> hashMap = new HashMap<>();
        Map<String, Double> treeMap = new TreeMap<>();
        String[] keys = {"AAPL","MSFT","GOOG","AMZN","TSLA","META","NVDA","NFLX","IBM","GS"};
        for (String k : keys) { hashMap.put(k, Math.random()); treeMap.put(k, Math.random()); }

        final int WARMUP  = 500_000;
        final int MEASURE = 2_000_000;

        // --- Warm-up phase (let JIT compile and optimise) ---
        double sink = 0;
        for (int i = 0; i < WARMUP; i++) sink += hashMap.get(keys[i % keys.length]);
        for (int i = 0; i < WARMUP; i++) sink += treeMap.get(keys[i % keys.length]);
        if (sink == 0) System.out.println("  (unreachable — prevents DCE)"); // use sink

        // --- Measurement phase ---
        long start = System.nanoTime();
        sink = 0;
        for (int i = 0; i < MEASURE; i++) sink += hashMap.get(keys[i % keys.length]);
        long hashMapNs = System.nanoTime() - start;

        start = System.nanoTime();
        sink = 0;
        for (int i = 0; i < MEASURE; i++) sink += treeMap.get(keys[i % keys.length]);
        long treeMapNs = System.nanoTime() - start;

        System.out.printf("  HashMap  : %,3d ns/op (total=%,d ms)  sink=%.0f%n",
                hashMapNs / MEASURE, hashMapNs / 1_000_000, sink);
        System.out.printf("  TreeMap  : %,3d ns/op (total=%,d ms)%n%n",
                treeMapNs / MEASURE, treeMapNs / 1_000_000);
    }

    // =========================================================================
    // B. Thread Dump (what `jstack <pid>` or kill -3 does)
    //    Shows all threads, their states, and stack traces.
    // =========================================================================
    static void threadDumpDemo() throws InterruptedException {
        System.out.println("--- B. Thread Dump (jstack equivalent) ---");

        // Create a few identifiable threads in various states
        Object lock = new Object();
        Thread blocker = new Thread(() -> {
            synchronized (lock) {
                try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, "HFT-RiskEngine");
        blocker.setDaemon(true);
        blocker.start();
        Thread.sleep(50); // let blocker acquire the lock

        Thread waiting = new Thread(() -> {
            synchronized (lock) { /* will block waiting for lock */ }
        }, "HFT-PnlCalculator");
        waiting.setDaemon(true);
        waiting.start();
        Thread.sleep(50);

        // jstack equivalent
        Map<Thread, StackTraceElement[]> allTraces = Thread.getAllStackTraces();
        System.out.printf("  Total threads in JVM: %d%n", allTraces.size());
        System.out.println("  Selected thread states:");

        for (Map.Entry<Thread, StackTraceElement[]> e : allTraces.entrySet()) {
            Thread t = e.getKey();
            if (t.getName().startsWith("HFT-") || t.getName().startsWith("main")) {
                System.out.printf("    %-25s  state=%-13s  daemon=%b%n",
                        t.getName(), t.getState(), t.isDaemon());
                StackTraceElement[] frames = e.getValue();
                for (int i = 0; i < Math.min(3, frames.length); i++) {
                    System.out.printf("       at %s%n", frames[i]);
                }
            }
        }
        blocker.interrupt();
        System.out.println();
    }

    // =========================================================================
    // C. Heap Analysis (jmap -histo equivalent)
    //    MemoryMXBean shows heap/non-heap usage; GC beans show collection stats.
    // =========================================================================
    static void heapAnalysisDemo() {
        System.out.println("--- C. Heap Analysis (jmap equivalent) ---");

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage    = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memBean.getNonHeapMemoryUsage();

        System.out.printf("  Heap   used=%,d KB  committed=%,d KB  max=%,d KB%n",
                heapUsage.getUsed()/1024, heapUsage.getCommitted()/1024, heapUsage.getMax()/1024);
        System.out.printf("  Non-Heap used=%,d KB  (JIT code, class metadata)%n%n",
                nonHeapUsage.getUsed()/1024);

        // Memory pool breakdown (Eden, Survivor, Old Gen)
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage u = pool.getUsage();
            if (u != null && u.getMax() > 0) {
                System.out.printf("  Pool %-30s  used=%,6d KB  max=%,6d KB%n",
                        pool.getName(), u.getUsed()/1024, u.getMax()/1024);
            }
        }
        System.out.println();
    }

    // =========================================================================
    // D. GC Pause Monitor (inline — what GC logs / async-profiler shows)
    //    Detects if a GC pause occurred between two checkpoints by comparing
    //    wall-clock time vs nanoTime delta. Any gap > threshold = GC pause.
    // =========================================================================
    static void gcPauseMonitor() {
        System.out.println("--- D. GC Pause Monitor ---");

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc : gcBeans) {
            System.out.printf("  GC %-30s  count=%,d  totalTime=%,d ms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }

        // Simulate GC trigger and detect pause inline
        long[] gcCountBefore = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).toArray();
        System.gc(); // trigger GC
        long[] gcCountAfter  = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).toArray();

        System.out.println("  After System.gc():");
        for (int i = 0; i < gcBeans.size(); i++) {
            long delta = gcCountAfter[i] - gcCountBefore[i];
            if (delta > 0) {
                System.out.printf("    [GC EVENT] %s fired %d time(s) — latency SLA at risk!%n",
                        gcBeans.get(i).getName(), delta);
            }
        }
        System.out.println();
    }

    // =========================================================================
    // E. Data Race Detection Pattern (what Helgrind / ThreadSanitizer finds)
    //    Shows BROKEN code (race) vs FIXED code (volatile / AtomicLong).
    // =========================================================================
    static void dataRaceDemo() throws InterruptedException {
        System.out.println("--- E. Data Race Detection (Helgrind / ThreadSanitizer) ---");

        // BROKEN: plain long — visibility not guaranteed across threads
        long[] racyCounter = {0};
        Thread t1 = new Thread(() -> { for (int i=0;i<1_000_000;i++) racyCounter[0]++; });
        Thread t2 = new Thread(() -> { for (int i=0;i<1_000_000;i++) racyCounter[0]++; });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.printf("  RACY   long counter: %,d (expected 2,000,000 — may differ)%n", racyCounter[0]);

        // FIXED: AtomicLong — CAS ensures no lost updates
        AtomicLong safeCounter = new AtomicLong(0);
        Thread t3 = new Thread(() -> { for (int i=0;i<1_000_000;i++) safeCounter.incrementAndGet(); });
        Thread t4 = new Thread(() -> { for (int i=0;i<1_000_000;i++) safeCounter.incrementAndGet(); });
        t3.start(); t4.start(); t3.join(); t4.join();
        System.out.printf("  SAFE   AtomicLong  : %,d (always exactly 2,000,000)%n", safeCounter.get());

        System.out.println("\n  In C++: ThreadSanitizer (TSan) detects the race at runtime.");
        System.out.println("  In Java: VisualVM / JCStress detects the same.");
        System.out.println("  In both: the fix is the same — use atomic/lock-free primitives.");
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================");
        System.out.println("  gcc/gdb/valgrind → Java Profiling Tools");
        System.out.println("====================================================\n");
        jmhStyleBenchmark();
        threadDumpDemo();
        heapAnalysisDemo();
        gcPauseMonitor();
        dataRaceDemo();
    }
}