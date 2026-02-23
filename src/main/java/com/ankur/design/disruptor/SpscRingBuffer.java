package com.ankur.design.disruptor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-Producer Single-Consumer (SPSC) ring buffer — the core of the
 * LMAX Disruptor pattern.
 *
 * Extracted from LockFreeDisruptorDemo for standalone use and testing.
 *
 * Design (from LockFreeDisruptorDemo.md):
 *
 *   data[]       — long[SIZE] pre-allocated once; zero allocation on hot path.
 *   producerSeq  — AtomicLong(-1); last sequence published by the producer.
 *   consumerSeq  — AtomicLong(-1); last sequence consumed by the consumer.
 *
 * Producer path  →  publish(value):
 *   1. Compute nextSeq = producerSeq + 1
 *   2. Back-pressure check: if (nextSeq - SIZE > consumerSeq) return false  (ring full)
 *   3. Write value into data[nextSeq & MASK]
 *   4. producerSeq.lazySet(nextSeq)  — store-release: data write is guaranteed
 *      visible before the sequence update (memory_order_release equivalent).
 *
 * Consumer path  →  consume():
 *   1. Compute nextConsumed = consumerSeq + 1
 *   2. Spin-wait until producerSeq >= nextConsumed  (Thread.onSpinWait = PAUSE)
 *   3. Read data[nextConsumed & MASK]
 *   4. consumerSeq.lazySet(nextConsumed)  — releases the slot back to the producer.
 */
public class SpscRingBuffer {

    public static final int  SIZE = 1 << 10;       // 1024 — power-of-2
    public static final int  MASK = SIZE - 1;       // 1023 — enables cheap & instead of %

    private final long[] data = new long[SIZE];     // pre-allocated; never grows

    // AtomicLong(-1): "nothing published / consumed yet"
    private final AtomicLong producerSeq = new AtomicLong(-1);
    private final AtomicLong consumerSeq = new AtomicLong(-1);

    /**
     * Producer: write {@code value} into the next slot.
     *
     * @return true if published; false if the ring is full (back-pressure).
     *         Caller must spin-retry on false:
     *         {@code while (!ring.publish(v)) Thread.onSpinWait();}
     */
    public boolean publish(long value) {
        long nextSeq = producerSeq.get() + 1;

        // Back-pressure: slot nextSeq maps to the same array index as (nextSeq - SIZE).
        // If the consumer hasn't freed that slot yet, we must not overwrite it.
        if (nextSeq - SIZE > consumerSeq.get()) return false;

        data[(int)(nextSeq & MASK)] = value;

        // store-release: guarantees the data write above is visible to the consumer
        // before the sequence number is updated (no StoreLoad fence needed).
        producerSeq.lazySet(nextSeq);
        return true;
    }

    /**
     * Consumer: spin-wait until the next slot is published, then return its value.
     * Advances consumerSeq with lazySet to release the slot back to the producer.
     */
    public long consume() {
        long nextConsumed = consumerSeq.get() + 1;

        // Busy-spin: Thread.onSpinWait() emits x86 PAUSE — reduces pipeline stalls.
        while (producerSeq.get() < nextConsumed) Thread.onSpinWait();

        long value = data[(int)(nextConsumed & MASK)];

        // store-release: frees the slot for the producer's back-pressure check.
        consumerSeq.lazySet(nextConsumed);
        return value;
    }

    /** Last sequence number published. -1 if nothing published yet. */
    public long producerSeq() { return producerSeq.get(); }

    /** Last sequence number consumed. -1 if nothing consumed yet. */
    public long consumerSeq() { return consumerSeq.get(); }
}