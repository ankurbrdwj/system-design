package com.ankur.design.hft.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 4 — Lock-Free Ring Buffer (MPSC: Multi-Producer Single-Consumer)
 *
 * No synchronized, no ReentrantLock, no OS calls.
 * Coordination is done entirely with:
 *   - AtomicLong for sequence numbers (CAS — one CPU instruction: LOCK CMPXCHG)
 *   - volatile reads to observe other threads' progress
 *
 * SPSC (single-producer single-consumer) is even simpler:
 *   producer writes, then does a lazySet (store-release, no full fence).
 *   consumer does a volatile read (load-acquire).
 *   No CAS needed — just one writer, one reader.
 *
 * MPSC (multi-producer) needs CAS because two producers race to claim the next slot:
 *
 *   Producer-1:  CAS(producerClaim, 5, 6)  → wins, gets slot 5
 *   Producer-2:  CAS(producerClaim, 5, 6)  → loses, retries with 6
 *
 * Two-phase commit for MPSC:
 *   1. CLAIM:   CAS on producerClaim to reserve a slot  (atomic)
 *   2. FILL:    write data into the reserved slot
 *   3. COMMIT:  set availableSeq[slot] = seq            (signals consumer)
 *
 * Consumer reads up to MIN(claimed) safely because it checks per-slot availability.
 *
 * Memory ordering:
 *   producerClaim.getAndIncrement()  →  full memory barrier (CAS)
 *   available[index].lazySet(seq)    →  store-release (ordered, no full fence)
 *   available[index].get()           →  volatile load-acquire
 */
public class RB4_LockFree {

    static final int CAPACITY = 8;
    static final int MASK     = CAPACITY - 1;

    private final String[]     buffer        = new String[CAPACITY];
    // per-slot availability: available[i] == seq means slot i holds event seq
    private final AtomicLong[] available;
    private final AtomicLong   producerClaim = new AtomicLong(0);  // next seq to claim
    private final AtomicLong   consumerSeq   = new AtomicLong(0);  // next seq to read

    @SuppressWarnings("unchecked")
    public RB4_LockFree() {
        available = new AtomicLong[CAPACITY];
        for (int i = 0; i < CAPACITY; i++)
            available[i] = new AtomicLong(-1);  // -1 = slot not yet published
    }

    // ── Producer (lock-free MPSC claim-fill-commit) ───────────────────────────

    public void publish(String event) throws InterruptedException {
        // PHASE 1: atomically claim the next sequence number
        long seq   = producerClaim.getAndIncrement();
        int  index = (int)(seq & MASK);

        // wait if consumer hasn't cleared this slot yet (buffer full guard)
        while (seq - consumerSeq.get() >= CAPACITY) {
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        // PHASE 2: fill the slot
        buffer[index] = event;

        // PHASE 3: commit — store-release makes event visible to consumer
        available[index].lazySet(seq);

        System.out.printf("[%-12s] PUBLISHED  seq=%-3d  index=%d  event='%s'%n",
                Thread.currentThread().getName(), seq, index, event);
    }

    // ── Consumer (lock-free read) ─────────────────────────────────────────────

    public String consume() throws InterruptedException {
        long seq   = consumerSeq.get();
        int  index = (int)(seq & MASK);

        // spin until producer has committed this exact slot
        // available[index] == seq means this slot's data is ready
        while (available[index].get() != seq) {
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        String event = buffer[index];
        consumerSeq.set(seq + 1);   // advance consumer

        System.out.printf("[consumer     ] CONSUMED   seq=%-3d  index=%d  event='%s'%n",
                seq, index, event);
        return event;
    }

    // ── Demo — two producers racing, one consumer ─────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        RB4_LockFree rb = new RB4_LockFree();
        int eventsPerProducer = 4;

        System.out.println("=== Lock-free MPSC: 2 producers racing to claim slots ===\n");

        Thread p1 = new Thread(() -> {
            try {
                for (int i = 0; i < eventsPerProducer; i++) rb.publish("P1-E" + i);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "producer-1");

        Thread p2 = new Thread(() -> {
            try {
                for (int i = 0; i < eventsPerProducer; i++) rb.publish("P2-E" + i);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "producer-2");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < eventsPerProducer * 2; i++) rb.consume();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "consumer");

        p1.start(); p2.start(); consumer.start();
        p1.join();  p2.join();  consumer.join();

        System.out.println("\nNo locks used. All coordination via CAS + volatile.");
    }
}