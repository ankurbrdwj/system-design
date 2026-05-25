package com.ankur.design.hft.optimizingjvm;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LESSON 2 — Thread.onSpinWait() vs yield() vs sleep(0)
 *
 * The talk claims:
 *   onSpinWait()  ~47  µs latency
 *   yield()       ~143 µs latency
 *   sleep(0)      ~1   ms+ latency
 *
 * Why they differ:
 *
 *   onSpinWait()
 *     Maps to x86 PAUSE instruction.
 *     PAUSE tells the CPU: "I'm spinning, don't speculate past here".
 *     - Reduces pipeline mis-speculation in spin loops (~10ns overhead)
 *     - Releases SMT (hyper-threading) resources to the sibling thread
 *     - Does NOT yield the OS thread — stays runnable
 *     Cost: ~10 ns (a few CPU cycles)
 *
 *   Thread.yield()
 *     Hints to the OS scheduler to give up the time slice.
 *     OS may or may not honour it. Involves a syscall.
 *     Cost: ~1-5 µs (scheduler round trip)
 *
 *   Thread.sleep(0) / LockSupport.parkNanos(1)
 *     Full OS park — thread leaves the run queue.
 *     Minimum sleep is one scheduler tick (~1ms on Linux, ~15ms on Windows).
 *     Cost: 1ms+ regardless of the 0 argument.
 *
 * This benchmark measures round-trip latency between two threads:
 *   producer sets a flag, consumer spins waiting to see it.
 *   We measure how long until the consumer observes the change.
 */
public class OPT2_SpinWaitBenchmark {

    static final int ROUNDS = 100_000;

    enum WaitStrategy { SPIN_WAIT, YIELD, SLEEP }

    static long measureLatency(WaitStrategy strategy) throws InterruptedException {
        AtomicBoolean signal  = new AtomicBoolean(false);
        AtomicLong    latency = new AtomicLong(0);

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < ROUNDS; i++) {
                long t0 = System.nanoTime();
                // spin until producer sets signal
                while (!signal.get()) {
                    switch (strategy) {
                        case SPIN_WAIT -> Thread.onSpinWait();
                        case YIELD     -> Thread.yield();
                        case SLEEP     -> {
                            try { Thread.sleep(0); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        }
                    }
                }
                latency.addAndGet(System.nanoTime() - t0);
                signal.set(false);
            }
        });

        Thread producer = new Thread(() -> {
            for (int i = 0; i < ROUNDS; i++) {
                while (signal.get()) Thread.onSpinWait();  // wait for consumer to reset
                signal.set(true);
            }
        });

        consumer.start();
        producer.start();
        consumer.join();
        producer.join();

        return latency.get() / ROUNDS;  // average nanoseconds per round trip
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Wait Strategy Latency Benchmark ===");
        System.out.println("Measuring average signal-to-observe latency over " + ROUNDS + " rounds\n");

        // warm up JIT before measuring
        measureLatency(WaitStrategy.SPIN_WAIT);

        long spinNs  = measureLatency(WaitStrategy.SPIN_WAIT);
        long yieldNs = measureLatency(WaitStrategy.YIELD);
        long sleepNs = measureLatency(WaitStrategy.SLEEP);

        System.out.printf("onSpinWait()   avg latency = %,7d ns  (%,.1f µs)%n",
                spinNs,  spinNs  / 1000.0);
        System.out.printf("yield()        avg latency = %,7d ns  (%,.1f µs)%n",
                yieldNs, yieldNs / 1000.0);
        System.out.printf("sleep(0)       avg latency = %,7d ns  (%,.1f µs)%n",
                sleepNs, sleepNs / 1000.0);

        System.out.printf("%nyield  is %.1fx slower than onSpinWait%n",
                (double) yieldNs / spinNs);
        System.out.printf("sleep  is %.1fx slower than onSpinWait%n",
                (double) sleepNs / spinNs);

        System.out.println("\nConclusion:");
        System.out.println("  Hot path ring buffer → onSpinWait()");
        System.out.println("  Moderate latency tolerance → yield()");
        System.out.println("  Don't care about latency / save CPU → sleep/park");
    }
}
