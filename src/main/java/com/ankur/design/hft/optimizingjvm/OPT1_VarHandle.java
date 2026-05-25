package com.ankur.design.hft.optimizingjvm;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * LESSON 1 — VarHandle: replacing sun.misc.Unsafe (Java 9+)
 *
 * Before Java 9, lock-free code used sun.misc.Unsafe for:
 *   - volatile reads/writes on plain fields
 *   - CAS (compareAndSet) on plain fields
 *   - Ordered (lazySet) stores
 *
 * Unsafe is private API — breaks on every JDK update.
 * VarHandle is the public, safe replacement with identical performance.
 *
 * Memory ordering modes (from weakest → strongest):
 * ┌─────────────────┬──────────────────────────────────────────────────┐
 * │ getOpaque       │ no reordering with other opaque accesses only    │
 * │ getAcquire      │ load-acquire: nothing after this moves before it │
 * │ getVolatile     │ full volatile: load-acquire + store-release fence│
 * │ compareAndSet   │ CAS: full fence, atomic                          │
 * │ setRelease      │ store-release: nothing before this moves after   │
 * │ setOpaque       │ like lazySet — ordered but no full fence         │
 * └─────────────────┴──────────────────────────────────────────────────┘
 *
 * SPSC ring buffer using VarHandle instead of AtomicLong:
 *
 *   Producer:  setRelease(seq + 1)    — store-release, pairs with consumer's load-acquire
 *   Consumer:  getAcquire(cursor)     — sees all writes before producer's setRelease
 *
 *   This avoids the full memory fence of volatile/AtomicLong on the producer side,
 *   which matters at 100M+ events/sec in HFT.
 */
public class OPT1_VarHandle {

    // ── BEFORE: AtomicLong (uses Unsafe internally) ───────────────────────────

    static class AtomicRingBuffer {
        private final long[]               buffer;
        private final int                  mask;
        private final java.util.concurrent.atomic.AtomicLong producerSeq = new java.util.concurrent.atomic.AtomicLong(-1);
        private final java.util.concurrent.atomic.AtomicLong consumerSeq = new java.util.concurrent.atomic.AtomicLong(-1);

        AtomicRingBuffer(int capacity) {
            buffer = new long[capacity];
            mask   = capacity - 1;
        }

        void publish(long value) {
            long seq   = producerSeq.get() + 1;
            buffer[(int)(seq & mask)] = value;
            producerSeq.set(seq);          // volatile write — full fence
        }

        long consume() {
            long seq = consumerSeq.get() + 1;
            while (producerSeq.get() < seq) Thread.onSpinWait();  // volatile read each spin
            long v = buffer[(int)(seq & mask)];
            consumerSeq.set(seq);
            return v;
        }
    }

    // ── AFTER: VarHandle with weaker orderings where safe ────────────────────

    static class VarHandleRingBuffer {
        private final long[] buffer;
        private final int    mask;

        private long producerSeq = -1;   // plain field — VarHandle controls ordering
        private long consumerSeq = -1;

        // obtain handles at class-load time (free after that)
        private static final VarHandle PRODUCER;
        private static final VarHandle CONSUMER;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                PRODUCER = lookup.findVarHandle(VarHandleRingBuffer.class, "producerSeq", long.class);
                CONSUMER = lookup.findVarHandle(VarHandleRingBuffer.class, "consumerSeq", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        VarHandleRingBuffer(int capacity) {
            buffer = new long[capacity];
            mask   = capacity - 1;
        }

        void publish(long value) {
            long seq = (long) PRODUCER.getAcquire(this) + 1;
            buffer[(int)(seq & mask)] = value;
            // setRelease: ensures buffer write is visible before the seq update
            // cheaper than full volatile — no store-load fence
            PRODUCER.setRelease(this, seq);
        }

        long consume() {
            long seq = (long) CONSUMER.getAcquire(this) + 1;
            // spin until producer has published this seq
            while ((long) PRODUCER.getAcquire(this) < seq) Thread.onSpinWait();
            long v = buffer[(int)(seq & mask)];
            CONSUMER.setRelease(this, seq);
            return v;
        }

        // ── CAS example: claim a slot atomically (MPSC producer) ─────────────
        boolean tryClaim(long expected, long next) {
            // compareAndSet: full memory fence, atomic
            return PRODUCER.compareAndSet(this, expected, next);
        }
    }

    // ── Correctness demo ─────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== VarHandle SPSC ring buffer ===\n");

        VarHandleRingBuffer rb = new VarHandleRingBuffer(8);
        int N = 10;

        Thread producer = new Thread(() -> {
            for (int i = 0; i < N; i++) {
                rb.publish(i * 100L);
                System.out.printf("[producer] published seq=%d  value=%d%n", i, i * 100);
            }
        });

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < N; i++) {
                long v = rb.consume();
                System.out.printf("[consumer] consumed seq=%d  value=%d%n", i, v);
            }
        });

        producer.start(); consumer.start();
        producer.join();  consumer.join();

        System.out.println("\nKey difference from AtomicLong:");
        System.out.println("  setRelease  = store-release only   (no full StoreLoad fence)");
        System.out.println("  getAcquire  = load-acquire only    (no full LoadStore fence)");
        System.out.println("  AtomicLong.set() = full volatile   (StoreLoad + LoadStore)");
        System.out.println("  On x86 the difference is small; on ARM it is significant.");
    }
}