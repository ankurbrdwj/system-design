package com.ankur.design.hft.ringbuffer;

/**
 * CONCEPT 1 — Ring Buffer core: sequence numbers + wrap-around
 *
 * A ring buffer is a fixed-size array where producer and consumer
 * move through it using ever-increasing sequence numbers.
 * The array index is computed with modulo (or bitmask).
 *
 * Layout (capacity=8):
 *
 *   index:  [0][1][2][3][4][5][6][7]
 *   seq:     0   1   2   3   4   5   6   7  8→0  9→1 ...
 *
 * Producer writes at:  producerSeq % capacity  (or producerSeq & MASK)
 * Consumer reads  at:  consumerSeq % capacity
 *
 * Buffer FULL  when: producerSeq - consumerSeq == capacity  → producer must wait
 * Buffer EMPTY when: producerSeq == consumerSeq             → consumer must wait
 *
 * WHY capacity must be a power of 2:
 *   seq % 8  requires a division instruction  (~20-30 cycles)
 *   seq & 7  is a single AND instruction      (~1 cycle)
 *   They give the same result when capacity is a power of 2.
 *   MASK = capacity - 1 = 0b0111 for capacity=8
 */
public class RB1_BasicRingBuffer {

    private final Object[] buffer;
    private final int      capacity;
    private final int      MASK;      // capacity - 1, used instead of % for speed

    private long producerSeq = 0;    // next slot to write
    private long consumerSeq = 0;    // next slot to read

    public RB1_BasicRingBuffer(int capacity) {
        // capacity MUST be power of 2 so bitwise & works as modulo
        if (Integer.bitCount(capacity) != 1)
            throw new IllegalArgumentException("capacity must be power of 2");
        this.capacity = capacity;
        this.MASK     = capacity - 1;
        this.buffer   = new Object[capacity];
    }

    // ── Producer ──────────────────────────────────────────────────────────────

    /**
     * Publish one event.
     * Blocks (busy-spins) if buffer is full — producer cannot overtake consumer.
     *
     *   full check:  producerSeq - consumerSeq == capacity
     *                one full lap ahead means all slots are occupied
     */
    public void publish(Object event) throws InterruptedException {
        // wait until there is a free slot
        while (producerSeq - consumerSeq == capacity) {
            // in real Disruptor this is a wait strategy (spin/yield/block)
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        int index = (int)(producerSeq & MASK);   // wrap-around via bitmask
        buffer[index] = event;

        System.out.printf("[producer] seq=%-3d  index=%d  wrote=%s%n",
                producerSeq, index, event);

        producerSeq++;   // advance AFTER write so consumer sees committed data
    }

    // ── Consumer ──────────────────────────────────────────────────────────────

    /**
     * Consume one event.
     * Blocks if buffer is empty — consumer cannot overtake producer.
     *
     *   empty check: consumerSeq == producerSeq
     */
    public Object consume() throws InterruptedException {
        while (consumerSeq == producerSeq) {
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        int    index = (int)(consumerSeq & MASK);
        Object event = buffer[index];

        System.out.printf("[consumer] seq=%-3d  index=%d  read=%s%n",
                consumerSeq, index, event);

        consumerSeq++;
        return event;
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        RB1_BasicRingBuffer rb = new RB1_BasicRingBuffer(4);  // capacity=4, MASK=3

        System.out.println("=== Wrap-around demo: publishing 6 events into capacity-4 buffer ===");
        System.out.println("Notice: index resets to 0 after 3  (seq & MASK)\n");

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 6; i++) {
                    rb.publish("E" + i);
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 6; i++) {
                    Thread.sleep(50);  // consumer slower → producer will block at +4
                    rb.consume();
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}