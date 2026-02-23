package com.ankur.design.core.trading;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free Data Structures → LMAX Disruptor
 *
 * From the README:
 *   "The SPSC ring buffer that is the workhorse of C++ HFT has a direct Java
 *    equivalent — the LMAX Disruptor. It was actually written in Java and is
 *    used in production HFT systems."
 *
 *   "AtomicLong with lazySet() (equivalent to memory_order_release) is how
 *    you build lock-free structures in Java."
 *
 *   "ConcurrentLinkedQueue — that's a lock-free queue under the hood."
 *
 * This demo implements:
 *   A. The SPSC (Single-Producer Single-Consumer) ring buffer — the core
 *      Disruptor pattern — using only AtomicLong and lazySet().
 *   B. ConcurrentLinkedQueue comparison (MPMC, lock-free, higher overhead).
 *   C. Latency benchmark: ring buffer vs CLQ.
 */
public class LockFreeDisruptorDemo {

    // =========================================================================
    // A. SPSC Ring Buffer with lazySet()
    //    Producer uses lazySet() (store-release) to publish a sequence number.
    //    Consumer spin-reads the sequence (load-acquire semantics via volatile).
    //
    //    This is the exact pattern inside the LMAX Disruptor and most C++ HFT
    //    ring buffers (std::atomic with memory_order_release / memory_order_acquire).
    // =========================================================================
    static final class SpscRingBuffer {
        private static final int SIZE = 1 << 10;  // 1024 — power-of-2 for cheap masking
        private static final int MASK = SIZE - 1;

        // Pre-allocated slots: reuse objects, zero GC pressure
        private final long[] data = new long[SIZE];

        // Separate cache lines to avoid false sharing between producer and consumer
        // (In C++ this is: alignas(64) std::atomic<long> producerSeq)
        private final AtomicLong producerSeq = new AtomicLong(-1);
        private final AtomicLong consumerSeq = new AtomicLong(-1);

        /** Producer: claim next slot, write value, then publish with lazySet. */
        boolean publish(long value) {
            long nextSeq = producerSeq.get() + 1;
            // Back-pressure: don't overwrite unconsumed slots
            if (nextSeq - SIZE > consumerSeq.get()) return false; // full

            data[(int)(nextSeq & MASK)] = value;
            // lazySet = memory_order_release: the data write above is guaranteed
            // to be visible before the sequence number update.
            producerSeq.lazySet(nextSeq);
            return true;
        }

        /** Consumer: spin-wait for next sequence; returns the value. */
        long consume() {
            long nextConsumed = consumerSeq.get() + 1;
            while (producerSeq.get() < nextConsumed) Thread.onSpinWait(); // busy-spin
            long value = data[(int)(nextConsumed & MASK)];
            consumerSeq.lazySet(nextConsumed); // release slot back to producer
            return value;
        }

        long producerSeq() { return producerSeq.get(); }
        long consumerSeq() { return consumerSeq.get(); }
    }

    // =========================================================================
    // B. ConcurrentLinkedQueue — MPMC lock-free queue (Michael & Scott algorithm)
    //    Backed by a linked list; allocates a node per element → GC pressure.
    //    Better for multiple producers/consumers; higher latency than ring buffer.
    // =========================================================================

    // =========================================================================
    // C. Benchmark
    // =========================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("====================================================");
        System.out.println("  Lock-free: SPSC Ring Buffer vs ConcurrentLinkedQueue");
        System.out.println("====================================================\n");

        int EVENTS = 2_000_000;

        // ---- SPSC Ring Buffer ----
        SpscRingBuffer ring   = new SpscRingBuffer();
        long[] ringLatencies  = new long[EVENTS];

        Thread ringConsumer = Thread.ofPlatform().name("ring-consumer").start(() -> {
            for (int i = 0; i < EVENTS; i++) {
                long publishedNs = ring.consume();
                ringLatencies[i] = System.nanoTime() - publishedNs;
            }
        });

        // Warm up: give consumer thread time to spin up
        Thread.sleep(50);

        long startRing = System.nanoTime();
        for (int i = 0; i < EVENTS; i++) {
            long now = System.nanoTime();
            while (!ring.publish(now)) Thread.onSpinWait(); // back-pressure spin
        }
        ringConsumer.join();
        long ringTotalNs = System.nanoTime() - startRing;

        // ---- ConcurrentLinkedQueue ----
        ConcurrentLinkedQueue<Long> clq = new ConcurrentLinkedQueue<>();
        long[] clqLatencies = new long[EVENTS];

        Thread clqConsumer = Thread.ofPlatform().name("clq-consumer").start(() -> {
            for (int i = 0; i < EVENTS; ) {
                Long ts = clq.poll();
                if (ts != null) { clqLatencies[i++] = System.nanoTime() - ts; }
                else Thread.onSpinWait();
            }
        });

        Thread.sleep(50);

        long startClq = System.nanoTime();
        for (int i = 0; i < EVENTS; i++) {
            clq.offer(System.nanoTime());
        }
        clqConsumer.join();
        long clqTotalNs = System.nanoTime() - startClq;

        // ---- Stats ----
        printStats("SPSC Ring Buffer (lazySet)", ringLatencies, EVENTS, ringTotalNs);
        printStats("ConcurrentLinkedQueue      ", clqLatencies, EVENTS, clqTotalNs);

        System.out.println("\nKey design points:");
        System.out.println("  • lazySet() = store-release (no StoreLoad fence) → cheaper than set()");
        System.out.println("  • Ring buffer: zero allocation after warm-up → zero GC");
        System.out.println("  • CLQ: allocates a Node per element → GC pressure");
        System.out.println("  • Both are lock-free: no mutex, no OS park/unpark");
    }

    private static void printStats(String name, long[] lats, int count, long totalNs) {
        java.util.Arrays.sort(lats, 0, count);
        long sum = 0; for (int i = 0; i < count; i++) sum += lats[i];
        System.out.printf("%n[%s]%n", name);
        System.out.printf("  Throughput : %,.0f M events/sec%n", count * 1e9 / totalNs / 1e6);
        System.out.printf("  Avg latency: %,d ns%n", sum / count);
        System.out.printf("  p50        : %,d ns%n", lats[count / 2]);
        System.out.printf("  p99        : %,d ns%n", lats[(int)(count * 0.99)]);
        System.out.printf("  p99.9      : %,d ns%n", lats[(int)(count * 0.999)]);
        System.out.printf("  Max        : %,d ns%n", lats[count - 1]);
    }
}