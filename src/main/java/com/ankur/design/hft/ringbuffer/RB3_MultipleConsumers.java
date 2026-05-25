package com.ankur.design.hft.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * CONCEPT 3 — Multiple Consumers + Slowest Consumer Tracking
 *
 * Each consumer has its OWN sequence number and reads independently.
 * The producer can only overwrite a slot when ALL consumers have passed it.
 *
 * Producer safe-to-write check:
 *   min(consumer sequences) must be > producerSeq - capacity
 *
 *   i.e. the SLOWEST consumer must have moved past the slot we want to reuse.
 *
 * Diagram (capacity=4, two consumers A and B):
 *
 *   slot:   [0]  [1]  [2]  [3]  [0]  [1] ...
 *   seq:     0    1    2    3    4    5  ...
 *
 *   consumerA.seq = 3   (processed up to seq 3)
 *   consumerB.seq = 1   (processed up to seq 1)  ← SLOWEST
 *
 *   producer wants to write seq 5 → index 1
 *   check: min(3,1) = 1, producerSeq - capacity = 5 - 4 = 1
 *   1 > 1 is FALSE → producer must WAIT for consumer B to advance past seq 1
 *
 * This guarantees: producer never overwrites data a slow consumer hasn't read yet.
 */
public class RB3_MultipleConsumers {

    static final int CAPACITY = 4;
    static final int MASK     = CAPACITY - 1;

    private final String[]    buffer      = new String[CAPACITY];
    private final AtomicLong  producerSeq = new AtomicLong(-1);

    // each consumer tracks its own read position
    static class Consumer {
        final String     name;
        final AtomicLong seq = new AtomicLong(-1);   // last seq processed

        Consumer(String name) { this.name = name; }
    }

    private final Consumer[] consumers;

    public RB3_MultipleConsumers(Consumer... consumers) {
        this.consumers = consumers;
    }

    // ── Producer ──────────────────────────────────────────────────────────────

    public void publish(String event) throws InterruptedException {
        long nextSeq = producerSeq.get() + 1;

        // wait until ALL consumers have cleared the slot we're about to overwrite
        while (true) {
            long minConsumerSeq = Long.MAX_VALUE;
            for (Consumer c : consumers)
                minConsumerSeq = Math.min(minConsumerSeq, c.seq.get());

            // slot is safe to overwrite when slowest consumer is >= (nextSeq - capacity)
            if (minConsumerSeq >= nextSeq - CAPACITY) break;

            System.out.printf("[producer] WAITING — slowest consumer seq=%d, need >=%d%n",
                    minConsumerSeq, nextSeq - CAPACITY);
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        int index = (int)(nextSeq & MASK);
        buffer[index] = event;
        producerSeq.set(nextSeq);

        System.out.printf("[producer] seq=%-3d  index=%d  wrote='%s'%n", nextSeq, index, event);
    }

    // ── Consumer ──────────────────────────────────────────────────────────────

    public void consumeNext(Consumer consumer) throws InterruptedException {
        long nextSeq = consumer.seq.get() + 1;

        // wait until producer has published this seq
        while (producerSeq.get() < nextSeq) {
            Thread.onSpinWait();
            if (Thread.interrupted()) throw new InterruptedException();
        }

        int    index = (int)(nextSeq & MASK);
        String event = buffer[index];

        System.out.printf("[%-10s] seq=%-3d  index=%d  read='%s'%n",
                consumer.name, nextSeq, index, event);

        consumer.seq.set(nextSeq);
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        Consumer fast = new Consumer("fast");
        Consumer slow = new Consumer("slow");

        RB3_MultipleConsumers rb = new RB3_MultipleConsumers(fast, slow);

        System.out.println("=== 2 consumers, slow one throttles the producer ===\n");

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 8; i++) rb.publish("E" + i);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread fastConsumer = new Thread(() -> {
            try {
                for (int i = 0; i < 8; i++) {
                    rb.consumeNext(fast);
                    Thread.sleep(10);   // fast: 10ms per event
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "fast");

        Thread slowConsumer = new Thread(() -> {
            try {
                for (int i = 0; i < 8; i++) {
                    rb.consumeNext(slow);
                    Thread.sleep(80);   // slow: 80ms per event → forces producer to wait
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "slow");

        producer.start();
        fastConsumer.start();
        slowConsumer.start();
        producer.join();
        fastConsumer.join();
        slowConsumer.join();

        System.out.println("\nDone. Both consumers processed all 8 events independently.");
    }
}