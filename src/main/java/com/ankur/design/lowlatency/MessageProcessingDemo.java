package com.ankur.design.lowlatency;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Keyword: Message Processing
 *
 * Demonstrates a single-writer, single-reader ring buffer (Disruptor pattern)
 * for ultra-low-latency message passing between threads WITHOUT LOCKS.
 *
 * Key principles:
 * - Fixed-size power-of-2 ring buffer for cheap index masking (& mask).
 * - Producer claims a slot, writes, then publishes by advancing a cursor.
 * - Consumer spins on the published cursor (busy-spin for minimum latency).
 * - No GC pressure: message objects are pre-allocated and reused (object pool).
 *
 * This mirrors the LMAX Disruptor design used in production trading systems.
 */
public class MessageProcessingDemo {

    /** Immutable market data message — reused via object pool. */
    static final class MarketDataEvent {
        volatile long sequence;
        volatile String symbol;
        volatile double bidPrice;
        volatile double askPrice;
        volatile long   quantity;
        volatile long   timestampNs;

        void populate(long seq, String sym, double bid, double ask, long qty) {
            this.sequence    = seq;
            this.symbol      = sym;
            this.bidPrice    = bid;
            this.askPrice    = ask;
            this.quantity    = qty;
            this.timestampNs = System.nanoTime();
        }
    }

    /** Lock-free single-producer single-consumer ring buffer. */
    static final class RingBuffer {
        private final MarketDataEvent[] buffer;
        private final int mask;

        // Padding to avoid false sharing between producer and consumer cursors
        private volatile long producerCursor = -1;
        private volatile long consumerCursor = -1;

        RingBuffer(int size) {
            if (Integer.bitCount(size) != 1) throw new IllegalArgumentException("Size must be power of 2");
            buffer = new MarketDataEvent[size];
            for (int i = 0; i < size; i++) buffer[i] = new MarketDataEvent();
            mask = size - 1;
        }

        /** Producer: claim next slot, returns the pre-allocated event for population. */
        MarketDataEvent nextEvent() {
            long next = producerCursor + 1;
            // Spin until consumer has freed the slot (buffer wrap-around check)
            while (next - buffer.length > consumerCursor) {
                Thread.onSpinWait();
            }
            return buffer[(int) (next & mask)];
        }

        /** Producer: publish the event at the given sequence. */
        void publish(long sequence) {
            producerCursor = sequence;
        }

        /** Consumer: spin until a new event is available; returns it. */
        MarketDataEvent consume() {
            long nextConsumed = consumerCursor + 1;
            while (producerCursor < nextConsumed) {
                Thread.onSpinWait();
            }
            return buffer[(int) (nextConsumed & mask)];
        }

        /** Consumer: advance the consumed cursor after processing. */
        void commitConsume() {
            consumerCursor++;
        }

        long producerCursor() { return producerCursor; }
        long consumerCursor() { return consumerCursor; }
    }

    // -------------------------------------------------------------------------

    private final RingBuffer ringBuffer;
    private final AtomicLong publishedCount = new AtomicLong();
    private final AtomicLong processedCount = new AtomicLong();
    private volatile boolean running = true;

    public MessageProcessingDemo(int bufferSize) {
        this.ringBuffer = new RingBuffer(bufferSize);
    }

    /** Start a dedicated producer thread that publishes {@code count} events. */
    public Thread startProducer(int count, String[] symbols) {
        return Thread.ofPlatform().name("producer").start(() -> {
            java.util.Random rng = new java.util.Random(1L);
            for (long seq = 0; seq < count; seq++) {
                MarketDataEvent event = ringBuffer.nextEvent();
                event.populate(
                        seq,
                        symbols[(int) (seq % symbols.length)],
                        100.0 + rng.nextDouble(),
                        100.5 + rng.nextDouble(),
                        100 + rng.nextInt(900)
                );
                ringBuffer.publish(seq);
                publishedCount.incrementAndGet();
            }
            System.out.println("[producer] Done publishing " + count + " events");
        });
    }

    /** Start a dedicated consumer thread that processes events until stopped. */
    public Thread startConsumer(Consumer<MarketDataEvent> handler) {
        return Thread.ofPlatform().name("consumer").start(() -> {
            while (running || ringBuffer.consumerCursor() < ringBuffer.producerCursor()) {
                MarketDataEvent event = ringBuffer.consume();
                handler.accept(event);
                ringBuffer.commitConsume();
                processedCount.incrementAndGet();
            }
            System.out.println("[consumer] Done processing " + processedCount.get() + " events");
        });
    }

    public void stop() { running = false; }

    public static void main(String[] args) throws Exception {
        int EVENT_COUNT = 1_000_000;
        String[] SYMBOLS = {"AAPL", "MSFT", "GOOG", "AMZN", "TSLA"};

        MessageProcessingDemo demo = new MessageProcessingDemo(1024);

        long[] latencies = new long[EVENT_COUNT];
        AtomicLong idx = new AtomicLong();

        Consumer<MarketDataEvent> handler = event -> {
            long i = idx.getAndIncrement();
            if (i < EVENT_COUNT) {
                latencies[(int) i] = System.nanoTime() - event.timestampNs;
            }
        };

        Thread consumer = demo.startConsumer(handler);
        Thread producer = demo.startProducer(EVENT_COUNT, SYMBOLS);

        producer.join();
        // Give consumer time to drain the buffer
        Thread.sleep(200);
        demo.stop();
        consumer.join(2000);

        // Statistics
        java.util.Arrays.sort(latencies);
        long sum = 0;
        for (long l : latencies) sum += l;

        System.out.println("\n=== Ring Buffer Message Processing Latency ===");
        System.out.printf("Events published : %d%n", demo.publishedCount.get());
        System.out.printf("Events consumed  : %d%n", demo.processedCount.get());
        System.out.printf("Avg latency      : %d ns%n", sum / EVENT_COUNT);
        System.out.printf("p50 latency      : %d ns%n", latencies[EVENT_COUNT / 2]);
        System.out.printf("p99 latency      : %d ns%n", latencies[(int)(EVENT_COUNT * 0.99)]);
        System.out.printf("p99.9 latency    : %d ns%n", latencies[(int)(EVENT_COUNT * 0.999)]);
        System.out.printf("Max latency      : %d ns%n", latencies[EVENT_COUNT - 1]);
        System.out.printf("Throughput       : %.1f M events/sec%n",
                demo.processedCount.get() / 1e6 / (sum / 1e9 / EVENT_COUNT * EVENT_COUNT));
    }
}