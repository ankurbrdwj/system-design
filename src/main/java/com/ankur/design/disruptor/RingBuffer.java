package com.ankur.design.disruptor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal ring buffer modelling the core concepts from Write.md:
 *
 *  1. Two-phase commit  — claim a slot, write to it, then commit.
 *  2. No wrap-around    — producer spins until the slowest consumer has
 *                         moved past the slot it wants to write into.
 *  3. Multiple producers — a separate claimSequence (ClaimStrategy) dishes
 *                          out unique slots; the cursor only advances in order.
 */
public class RingBuffer {

    /** One pre-allocated slot in the ring. */
    public static class Entry {
        public volatile long sequence;
        public volatile long value;

        Entry(long sequence) { this.sequence = sequence; }
    }

    private final int       capacity;
    private final int       mask;
    private final Entry[]   entries;

    /** Last published (visible to consumers) sequence. */
    private final AtomicLong cursor       = new AtomicLong(-1);

    /** Next sequence to hand out to a producer (ClaimStrategy). */
    private final AtomicLong claimSeq     = new AtomicLong(-1);

    /** Registered consumer cursors — ProducerBarrier checks these to avoid wrap. */
    private final AtomicLong[] consumers;

    public RingBuffer(int capacity, int consumerCount) {
        if (Integer.bitCount(capacity) != 1)
            throw new IllegalArgumentException("Capacity must be a power of 2");
        this.capacity  = capacity;
        this.mask      = capacity - 1;
        this.entries   = new Entry[capacity];
        for (int i = 0; i < capacity; i++) entries[i] = new Entry(i);

        this.consumers = new AtomicLong[consumerCount];
        for (int i = 0; i < consumerCount; i++) consumers[i] = new AtomicLong(-1);
    }

    // -------------------------------------------------------------------------
    // Producer API  (two-phase commit)
    // -------------------------------------------------------------------------

    /**
     * Phase 1 — claim the next available slot.
     * Spins (back-pressure) until the slowest consumer has freed the slot.
     */
    public Entry nextEntry() {
        long next = claimSeq.incrementAndGet();

        // Wait until the slowest consumer is far enough ahead that we won't overwrite.
        long wrapPoint = next - capacity;
        while (slowestConsumer() <= wrapPoint) {
            Thread.onSpinWait();
        }

        Entry entry = entries[(int)(next & mask)];
        entry.sequence = next;
        return entry;
    }

    /**
     * Phase 2 — commit: wait for the cursor to reach (entry.sequence - 1)
     * then advance it.  Ordering guarantee: commits happen in sequence order.
     */
    public void commit(Entry entry) {
        long expected = entry.sequence - 1;
        while (cursor.get() != expected) {
            Thread.onSpinWait(); // wait for earlier producers to commit first
        }
        cursor.set(entry.sequence);
    }

    // -------------------------------------------------------------------------
    // Consumer API
    // -------------------------------------------------------------------------

    /** Read an entry by absolute sequence number (consumers track their own cursor). */
    public Entry read(long sequence) {
        while (cursor.get() < sequence) {
            Thread.onSpinWait(); // spin until the producer has published this sequence
        }
        return entries[(int)(sequence & mask)];
    }

    /** Consumer advances its own cursor after processing. */
    public void advanceConsumer(int consumerId, long sequence) {
        consumers[consumerId].set(sequence);
    }

    // -------------------------------------------------------------------------
    // Accessors (used in tests and by ProducerBarrier)
    // -------------------------------------------------------------------------

    public long getCursor()   { return cursor.get(); }
    public int  getCapacity() { return capacity; }

    /** Minimum consumer sequence — used for wrap-around check. */
    long slowestConsumer() {
        long min = Long.MAX_VALUE;
        for (AtomicLong c : consumers) min = Math.min(min, c.get());
        return min;
    }
}