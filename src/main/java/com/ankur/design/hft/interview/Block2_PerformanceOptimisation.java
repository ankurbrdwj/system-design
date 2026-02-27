package com.ankur.design.hft.interview;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Block 2: Performance Optimisation
 *
 * Interview questions answered here:
 *   Q1. "Explain false sharing and how would you detect and fix it?"
 *   Q2. "How do you avoid garbage generation in a hot trading path?"
 *   Q3. "What GC would you use for a low-latency trading system and why?"
 *   Q4. "How would you diagnose a latency spike that happens randomly?"
 *
 * Sections:
 *   1. FALSE SHARING     — two hot counters on same cache line vs padded fix
 *   2. OBJECT POOLING    — pre-allocate & recycle; zero GC on hot path
 *   3. OFF-HEAP MEMORY   — DirectByteBuffer; GC never touches this memory
 *   4. GC & JVM FLAGS    — documented reference for trading JVM tuning
 */
public class Block2_PerformanceOptimisation {

    // =========================================================================
    // 1. FALSE SHARING
    //
    // Q: "Explain false sharing and how would you detect and fix it."
    //
    // CPUs transfer memory in 64-byte CACHE LINES (8 longs side by side).
    // If Thread-A writes counterA and Thread-B writes counterB, but both
    // variables sit on the SAME 64-byte cache line, every write by A
    // invalidates B's cached line and vice-versa (MESI protocol).
    // Result: constant cache invalidation → ~100× slower than padded version.
    //
    // Detection: async-profiler -e cache-misses, Intel VTune, perf stat -e LLC-load-misses
    //
    // Fix option 1: @jdk.internal.vm.annotation.Contended (requires --add-opens)
    // Fix option 2: manual 56-byte padding between hot fields (shown below)
    //               → pads the struct to 64 bytes so each field is on its own line
    //
    // Real example: LMAX Disruptor uses 7 long padding values around its
    //               sequence number for exactly this reason (see disruptor/Readme.md).
    // =========================================================================

    /** UNPADDED: counterA and counterB likely share the same 64-byte cache line. */
    static final class UnpaddedCounters {
        volatile long counterA = 0;   // ← these two are probably on the same line
        volatile long counterB = 0;
    }

    /**
     * PADDED — uses the LMAX Disruptor inheritance trick.
     *
     * WHY NOT plain fields: p1..p7 declared but never read/written → the JIT
     * is free to eliminate them entirely, defeating the padding purpose.
     *
     * WHY INHERITANCE WORKS: the JVM spec guarantees base-class fields are
     * laid out in memory BEFORE subclass fields. The JIT cannot reorder or
     * remove inherited fields because it cannot prove no subclass reads them.
     *
     * Memory layout guaranteed:
     *   [16B obj header]
     *   [56B CounterAPadding.p1..p7]   ← fills rest of first cache line
     *   [8B  CounterAValue.counterA]    ← sits alone on its own cache line
     *   [56B CounterBPadding.p8..p14]  ← fills rest of that line
     *   [8B  PaddedCounters.counterB]   ← sits alone on its own cache line
     *
     * This is exactly how Disruptor's Sequence class is padded (see disruptor/Readme.md).
     */
    static abstract class CounterAPadding {
        // 7 longs = 56 bytes; pushes counterA to the start of the next cache line
        protected long p1, p2, p3, p4, p5, p6, p7;
    }
    static abstract class CounterAValue extends CounterAPadding {
        protected volatile long counterA = 0;
    }
    static abstract class CounterBPadding extends CounterAValue {
        // 7 more longs = 56 bytes; pushes counterB to the start of the next cache line
        protected long p8, p9, p10, p11, p12, p13, p14;
    }
    static final class PaddedCounters extends CounterBPadding {
        volatile long counterB = 0;
    }

    static long benchCounters(boolean padded, int iterations) throws InterruptedException {
        UnpaddedCounters unpadded = new UnpaddedCounters();
        PaddedCounters   pads     = new PaddedCounters();

        Thread t1, t2;
        if (padded) {
            t1 = new Thread(() -> { for (int i = 0; i < iterations; i++) pads.counterA++; });
            t2 = new Thread(() -> { for (int i = 0; i < iterations; i++) pads.counterB++; });
        } else {
            t1 = new Thread(() -> { for (int i = 0; i < iterations; i++) unpadded.counterA++; });
            t2 = new Thread(() -> { for (int i = 0; i < iterations; i++) unpadded.counterB++; });
        }

        long start = System.nanoTime();
        t1.start(); t2.start();
        t1.join();  t2.join();
        return System.nanoTime() - start;
    }

    // =========================================================================
    // 2. OBJECT POOLING
    //
    // Q: "How do you avoid garbage generation in a hot trading path?"
    //
    // Strategy:
    //   BEFORE the market opens (cold path):  pre-allocate all objects you will
    //                                          ever need during trading.
    //   DURING trading (hot path):            acquire from pool, use, release back.
    //   NEVER call new() inside the matching engine or order processing loop.
    //
    // Pool implementation: ArrayBlockingQueue (bounded, pre-filled at startup).
    //   acquire() — poll() returns null if pool exhausted (new() as last resort)
    //   release() — offer() returns to pool; discard if pool full (rare)
    //
    // Other strategies (also mention in interview):
    //   - ThreadLocal<T> — per-thread pre-allocated object, zero contention
    //   - Flyweight       — single shared mutable instance (single-threaded only)
    //   - Off-heap        — DirectByteBuffer; GC never sees it (Section 3)
    // =========================================================================

    static final class ExecutionReport {
        long   orderId;
        String symbol;
        double fillPrice;
        long   fillQty;
        char   side;

        void reset(long id, String sym, double px, long qty, char side) {
            this.orderId   = id;
            this.symbol    = sym;
            this.fillPrice = px;
            this.fillQty   = qty;
            this.side      = side;
        }
    }

    static final class ExecutionReportPool {
        private final ArrayBlockingQueue<ExecutionReport> pool;
        private final AtomicLong allocations = new AtomicLong();

        ExecutionReportPool(int capacity) {
            pool = new ArrayBlockingQueue<>(capacity);
            for (int i = 0; i < capacity; i++) {
                pool.offer(new ExecutionReport());
                allocations.incrementAndGet();
            }
        }

        /** Hot path: poll from pool, never blocks, never allocates (usually). */
        ExecutionReport acquire() {
            ExecutionReport r = pool.poll();
            if (r == null) {                    // pool exhausted — emergency alloc
                r = new ExecutionReport();
                allocations.incrementAndGet();
            }
            return r;
        }

        /** Hot path: return to pool for reuse; zero allocation. */
        void release(ExecutionReport r) {
            r.reset(0, null, 0, 0, ' ');
            pool.offer(r);                      // discard if pool full
        }

        long totalAllocations() { return allocations.get(); }
    }

    // =========================================================================
    // 3. OFF-HEAP MEMORY (DirectByteBuffer)
    //
    // ByteBuffer.allocateDirect() allocates memory OUTSIDE the JVM heap.
    // The GC never scans it, never moves it, never pauses for it.
    //
    // Use case: store the entire order book off-heap so a GC pause never
    // interrupts the matching engine reading price levels.
    //
    // Layout per order book entry (32 bytes):
    //   offset 0  : orderId  (8B, long)
    //   offset 8  : price    (8B, double) — fixed-point preferred in production
    //   offset 16 : quantity (8B, long)
    //   offset 24 : side     (1B) + 7B padding
    //
    // Chronicle Map / Chronicle Queue are production libraries built on this idea.
    // =========================================================================

    static final class OffHeapOrderBook {
        private static final int RECORD_SIZE = 32;
        private final ByteBuffer buffer;   // lives in native memory, not heap
        private final int        capacity;
        private int              count;

        OffHeapOrderBook(int capacity) {
            this.capacity = capacity;
            this.buffer   = ByteBuffer.allocateDirect(capacity * RECORD_SIZE);
        }

        void addOrder(long orderId, double price, long qty, char side) {
            if (count >= capacity) return;
            int base = count * RECORD_SIZE;
            buffer.putLong(base,     orderId);
            buffer.putDouble(base+8, price);
            buffer.putLong(base+16,  qty);
            buffer.put(base+24,      (byte) side);
            count++;
        }

        long   getOrderId(int i) { return buffer.getLong(i * RECORD_SIZE); }
        double getPrice(int i)   { return buffer.getDouble(i * RECORD_SIZE + 8); }
        long   getQty(int i)     { return buffer.getLong(i * RECORD_SIZE + 16); }
        char   getSide(int i)    { return (char) buffer.get(i * RECORD_SIZE + 24); }
        int    size()            { return count; }
    }

    // =========================================================================
    // 4. GC & JVM FLAGS REFERENCE
    //
    // Q: "What GC would you use for a low-latency trading system and why?"
    //
    // G1GC (default Java 9+):
    //   -XX:+UseG1GC -XX:MaxGCPauseMillis=10
    //   Good general-purpose; predictable pause targets; acceptable for most trading.
    //   Pause: typically 5–20ms. NOT suitable for sub-millisecond latency SLAs.
    //
    // ZGC (Java 15+ production-ready):
    //   -XX:+UseZGC
    //   Concurrent GC — collection happens while application runs.
    //   Pause: <1ms (just a single short stop-the-world for root scanning).
    //   Best choice for trading — Coinbase uses Azul Zing (similar concept).
    //
    // Shenandoah:
    //   -XX:+UseShenandoahGC
    //   Similar to ZGC; RedHat-backed; also sub-millisecond pauses.
    //
    // Essential JVM flags for trading:
    //   -Xms4g -Xmx4g          → equal min/max: prevent heap resize pauses
    //   -XX:+AlwaysPreTouch     → fault all heap pages at startup; no page fault during trading
    //   -XX:+DisableExplicitGC  → prevent System.gc() calls from libraries
    //   -Xlog:gc*               → structured GC logging (replaces -verbose:gc in Java 9+)
    //   -XX:+UseNUMA            → NUMA-aware allocation for multi-socket servers
    //   -XX:+UseTransparentHugePages → reduce TLB misses on large heaps (Linux)
    //
    // Diagnosis for random latency spikes (Q: "How would you diagnose..."):
    //   1. -Xlog:gc*:file=gc.log   — check if spike aligns with a GC pause
    //   2. async-profiler -e wall   — wall-clock profiling catches ALL thread states
    //   3. netdata / perf stat      — check for context switches, IRQ affinity issues
    //   4. numactl --hardware       — check cross-NUMA memory access
    //   5. lscpu / /proc/interrupts — verify NIC interrupts not sharing CPU with hot thread
    // =========================================================================

    static void printGCReference() {
        System.out.println("  GC Choice for Trading:");
        System.out.printf("    %-20s pause=5–20ms  target: general trading apps%n",    "G1GC");
        System.out.printf("    %-20s pause=<1ms    target: HFT / ultra-low latency%n",  "ZGC (Java 15+)");
        System.out.printf("    %-20s pause=<1ms    target: HFT (RedHat-backed)%n",      "Shenandoah");
        System.out.printf("    %-20s pause=NONE    target: Coinbase (commercial)%n",     "Azul Zing");
        System.out.println();
        System.out.println("  Key JVM flags:");
        System.out.println("    -Xms4g -Xmx4g              → no heap resize pauses");
        System.out.println("    -XX:+UseZGC                 → sub-ms GC for trading");
        System.out.println("    -XX:+AlwaysPreTouch         → no page faults during trading");
        System.out.println("    -XX:+DisableExplicitGC      → ignore System.gc() calls");
        System.out.println("    -Xlog:gc*                   → GC logging");
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) throws Exception {
        final int ITERATIONS = 50_000_000;
        System.out.println("=== Block 2: Performance Optimisation ===");
        System.out.println();

        // 1. False sharing benchmark
        System.out.println("--- 1. False Sharing ---");
        // warm up
        benchCounters(false, 100_000);
        benchCounters(true,  100_000);

        long unpaddedNs = benchCounters(false, ITERATIONS);
        long paddedNs   = benchCounters(true,  ITERATIONS);

        System.out.printf("  Unpadded (shared cache line): %,d ms%n", unpaddedNs / 1_000_000);
        System.out.printf("  Padded   (separate lines)   : %,d ms%n", paddedNs   / 1_000_000);
        System.out.printf("  Speedup from padding        : %.1fx%n", (double) unpaddedNs / paddedNs);
        System.out.println("  Fix: 7 × long padding fields between hot volatile longs");
        System.out.println("       OR @jdk.internal.vm.annotation.Contended (JDK internal)");
        System.out.println();

        // 2. Object pooling
        System.out.println("--- 2. Object Pool — zero allocation on hot path ---");
        int POOL_SIZE   = 1024;
        int ORDER_COUNT = 200_000;

        ExecutionReportPool pool = new ExecutionReportPool(POOL_SIZE);

        // warm-up JIT
        for (int i = 0; i < 10_000; i++) {
            ExecutionReport r = pool.acquire();
            r.reset(i, "WARM", 100.0, 10, 'B');
            pool.release(r);
        }
        System.gc(); Thread.sleep(100);

        long start = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < ORDER_COUNT; i++) {
            ExecutionReport r = pool.acquire();
            r.reset(i, "AAPL", 150.0 + i * 0.001, 100, 'B');
            checksum += r.orderId;
            pool.release(r);
        }
        long poolNs = System.nanoTime() - start;

        System.out.printf("  Processed  : %,d execution reports%n", ORDER_COUNT);
        System.out.printf("  Allocations: %,d (pool pre-allocated %,d; ZERO new() on hot path)%n",
                pool.totalAllocations(), POOL_SIZE);
        System.out.printf("  Time       : %,d ms  (%,d ns/report)  checksum=%d%n",
                poolNs / 1_000_000, poolNs / ORDER_COUNT, checksum);
        System.out.println();

        // 3. Off-heap
        System.out.println("--- 3. Off-Heap DirectByteBuffer ---");
        OffHeapOrderBook book = new OffHeapOrderBook(10);
        book.addOrder(1001, 150.25, 500, 'B');
        book.addOrder(1002, 150.30, 300, 'S');
        for (int i = 0; i < book.size(); i++) {
            System.out.printf("  [%d] orderId=%d  price=%.2f  qty=%d  side=%c%n",
                    i, book.getOrderId(i), book.getPrice(i), book.getQty(i), book.getSide(i));
        }
        System.out.println("  ^ stored in native memory — GC never scans or pauses for this");
        System.out.println();

        // 4. GC reference
        System.out.println("--- 4. GC & JVM Flags Reference ---");
        printGCReference();
    }
}