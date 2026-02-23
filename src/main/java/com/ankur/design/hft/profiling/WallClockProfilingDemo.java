package com.ankur.design.hft.profiling;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * CONCEPT 8: Wall-Clock Profiling — Detecting Wait Time
 *
 * ReadMe: "Wall Clock Profiling: samples thread states regardless of
 *          running/sleeping status, useful for detecting wait times."
 *          "Socket I/O Test: differentiated between busy and idle socket
 *          threads by excluding sleeping threads via async-profiler."
 *
 * CPU profiling mode:   samples only RUNNABLE threads (burning CPU).
 * Wall-clock mode:      samples ALL thread states — RUNNABLE, WAITING, BLOCKED.
 *   asprof -e wall -d 10 <pid>
 *
 * Use wall-clock when:
 *   - A request is slow but CPU usage is LOW
 *   - Threads spend most time waiting on I/O, locks, or sleep
 *   - You want to see the full elapsed time, not just on-CPU time
 *
 * This demo shows threads in four states and how each looks in
 * CPU-mode vs wall-clock-mode profiling.
 */
public class WallClockProfilingDemo {

    // =========================================================================
    // Simulated profiler: captures thread states at regular intervals
    // =========================================================================
    static final class WallClockProfiler {
        record Sample(String threadName, Thread.State state, String topFrame) {}

        private final CopyOnWriteArrayList<Sample> samples = new CopyOnWriteArrayList<>();
        private volatile boolean running = true;

        Thread start(int intervalMs) {
            return Thread.ofPlatform().daemon(true).name("wall-profiler").start(() -> {
                while (running) {
                    // Wall-clock mode: sample ALL threads regardless of state
                    Thread.getAllStackTraces().forEach((t, stack) -> {
                        if (t.getName().startsWith("scenario-")) {
                            String top = stack.length > 0
                                    ? stack[0].getMethodName()
                                    : "(empty stack)";
                            samples.add(new Sample(t.getName(), t.getState(), top));
                        }
                    });
                    try { Thread.sleep(intervalMs); } catch (InterruptedException e) { break; }
                }
            });
        }

        void stop() { running = false; }

        void report() {
            System.out.println("\n=== Wall-Clock Profiler Samples ===");
            System.out.printf("  %-30s %-15s %s%n", "Thread", "State", "Top Frame");
            System.out.println("  " + "-".repeat(70));

            // Aggregate: count how many samples each thread spent in each state
            java.util.Map<String, java.util.Map<Thread.State, Integer>> agg = new java.util.TreeMap<>();
            for (Sample s : samples) {
                agg.computeIfAbsent(s.threadName(), k -> new java.util.EnumMap<>(Thread.State.class))
                   .merge(s.state(), 1, Integer::sum);
            }

            int total = samples.size();
            agg.forEach((name, states) -> {
                int threadTotal = states.values().stream().mapToInt(Integer::intValue).sum();
                states.entrySet().stream()
                        .sorted(java.util.Map.Entry.<Thread.State,Integer>comparingByValue().reversed())
                        .forEach(e -> System.out.printf(
                                "  %-30s %-15s %5d samples (%4.0f%%)%n",
                                name, e.getKey(), e.getValue(),
                                100.0 * e.getValue() / threadTotal));
            });

            System.out.printf("%n  Total samples: %d%n", total);
        }
    }

    // =========================================================================
    // Four thread scenarios
    // =========================================================================

    /** Scenario 1: CPU-BOUND — burns 100% of CPU (always RUNNABLE). */
    static void cpuBoundWork(CountDownLatch done) {
        long sum = 0;
        long end = System.nanoTime() + 2_000_000_000L; // 2 seconds
        while (System.nanoTime() < end) sum++;          // busy loop
        done.countDown();
        if (sum == 0) System.out.print(""); // prevent DCE
    }

    /** Scenario 2: I/O WAIT — sleeps simulating blocked I/O (TIMED_WAITING). */
    static void ioWaitWork(CountDownLatch done) {
        try {
            Thread.sleep(2_000);  // simulates waiting for a network response
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        done.countDown();
    }

    /** Scenario 3: LOCK WAIT — blocked on a mutex (BLOCKED state). */
    static void lockWaitWork(Object sharedLock, CountDownLatch done) {
        synchronized (sharedLock) {       // tries to acquire — likely BLOCKED
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        done.countDown();
    }

    /**
     * Scenario 4: MIXED — alternates between CPU work and I/O wait.
     * In CPU mode: looks like a busy thread.
     * In wall-clock mode: reveals it sleeps 80% of the time.
     */
    static void mixedWork(CountDownLatch done) {
        try {
            for (int i = 0; i < 10; i++) {
                // 10% CPU
                long end = System.nanoTime() + 20_000_000L; // 20ms work
                long sum = 0;
                while (System.nanoTime() < end) sum++;

                // 90% wait
                Thread.sleep(180);  // 180ms sleep
                if (sum == 0) System.out.print("");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        done.countDown();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("====================================================");
        System.out.println("  Wall-Clock Profiling Demo");
        System.out.println("====================================================");
        System.out.println("\nRunning 4 thread scenarios for 2 seconds...\n");
        System.out.println("  scenario-cpu-bound  → always RUNNABLE  (CPU profiler sees this)");
        System.out.println("  scenario-io-wait    → always WAITING   (CPU profiler MISSES this)");
        System.out.println("  scenario-lock-wait  → usually BLOCKED  (CPU profiler MISSES this)");
        System.out.println("  scenario-mixed      → 10% CPU / 90% wait (CPU profiler misleading)\n");

        WallClockProfiler profiler = new WallClockProfiler();
        CountDownLatch done = new CountDownLatch(4);

        // Shared lock for scenario 3
        Object sharedLock = new Object();

        // Lock holder: holds the lock for the full duration so lockWaitWork blocks
        Thread lockHolder = Thread.ofPlatform().name("lock-holder").daemon(true).start(() -> {
            synchronized (sharedLock) {
                try { Thread.sleep(2_000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread.sleep(50); // let lock holder acquire the lock

        // Start all four scenarios
        Thread.ofPlatform().name("scenario-cpu-bound").start(()  -> cpuBoundWork(done));
        Thread.ofPlatform().name("scenario-io-wait").start(()    -> ioWaitWork(done));
        Thread.ofPlatform().name("scenario-lock-wait").start(()  -> lockWaitWork(sharedLock, done));
        Thread.ofPlatform().name("scenario-mixed").start(()      -> mixedWork(done));

        // Start wall-clock profiler
        Thread profilerThread = profiler.start(50); // sample every 50ms

        done.await(10, TimeUnit.SECONDS);
        profiler.stop();
        profilerThread.join(500);

        profiler.report();

        System.out.println("\nKey insight:");
        System.out.println("  CPU profiler (-e cpu):   only samples RUNNABLE threads");
        System.out.println("    → sees cpu-bound, misses io-wait and lock-wait entirely");
        System.out.println("    → mixed thread looks ~100% busy (wrong!)");
        System.out.println();
        System.out.println("  Wall-clock profiler (-e wall): samples ALL states");
        System.out.println("    → sees io-wait thread spending 100% in sleep/park");
        System.out.println("    → sees lock-wait spending 100% BLOCKED on monitor");
        System.out.println("    → reveals mixed thread is actually 90% idle");
        System.out.println();
        System.out.println("  Use wall-clock when: request is slow but CPU% is LOW");
        System.out.println("  Use CPU profiling when: machine is hot and you need to find the hot loop");
    }
}