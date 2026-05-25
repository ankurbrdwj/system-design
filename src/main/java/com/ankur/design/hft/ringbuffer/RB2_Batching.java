package com.ankur.design.hft.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 2 — Batching
 *
 * Instead of consuming one event per loop iteration, the consumer
 * checks how many events are AVAILABLE in one go and processes all of them.
 *
 * Why batching matters:
 *   Without batching:  consumer calls availableSeq check N times (N volatile reads)
 *   With batching:     consumer calls availableSeq check once, processes N events cheaply
 *
 * Producer side batching: claim multiple slots in one step, fill them, publish once.
 *   This amortises the cost of the memory fence on publish.
 *
 * Timeline (capacity=8, consumer batch-reads up to 4 at once):
 *
 *   Producer writes: seq 0,1,2,3,4,5 quickly
 *   Consumer wakes:  sees availableSeq=5, reads 0..5 in ONE batch pass
 *                    instead of waking up 6 separate times
 *
 * This is a key reason Disruptor outperforms BlockingQueue in throughput.
 */
public class RB2_Batching {

    static final int CAPACITY = 8;
    static final int MASK     = CAPACITY - 1;

    private final long[]      buffer       = new long[CAPACITY];
    private final AtomicLong  producerSeq  = new AtomicLong(-1);  // -1 = nothing published
    private       long        consumerSeq  = 0;

    // ── Producer: publish a batch ─────────────────────────────────────────────

    /**
     * Publish `count` events in one batch.
     * Fills slots then advances producerSeq in a single atomic write.
     * The memory fence fires ONCE for the whole batch, not once per event.
     */
    public void publishBatch(long startValue, int count) throws InterruptedException {
        long claimedEnd = producerSeq.get() + count;

        // wait for consumer to free enough slots
        while (claimedEnd - consumerSeq >= CAPACITY) {
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        // fill all slots first
        for (int i = 0; i < count; i++) {
            long seq   = producerSeq.get() + 1 + i;
            int  index = (int)(seq & MASK);
            buffer[index] = startValue + i;
        }

        // publish: one volatile write exposes ALL filled slots to consumers
        producerSeq.set(claimedEnd);

        System.out.printf("[producer] batch published seq %d..%d  (%d events)%n",
                claimedEnd - count + 1, claimedEnd, count);
    }

    // ── Consumer: drain everything available in one batch ─────────────────────

    /**
     * Read all events that are available right now — one pass, no repeated fence reads.
     * Returns number of events consumed.
     */
    public int consumeAvailable() {
        long available = producerSeq.get();       // ONE volatile read
        if (available < consumerSeq) return 0;    // nothing new

        int processed = 0;
        while (consumerSeq <= available) {
            int   index = (int)(consumerSeq & MASK);
            long  value = buffer[index];
            System.out.printf("[consumer] seq=%-3d  index=%d  value=%d%n",
                    consumerSeq, index, value);
            consumerSeq++;
            processed++;
        }
        return processed;
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        RB2_Batching rb = new RB2_Batching();

        System.out.println("=== Producer publishes in batches, consumer drains all available ===\n");

        Thread producer = new Thread(() -> {
            try {
                rb.publishBatch(100, 3);   // publish 100,101,102
                Thread.sleep(50);
                rb.publishBatch(200, 4);   // publish 200,201,202,203
                Thread.sleep(50);
                rb.publishBatch(300, 2);   // publish 300,301
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int round = 0; round < 5; round++) {
                    Thread.sleep(70);
                    int n = rb.consumeAvailable();
                    if (n > 0)
                        System.out.printf("[consumer] batch consumed %d events%n\n", n);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}