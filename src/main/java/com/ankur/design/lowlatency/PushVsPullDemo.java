package com.ankur.design.lowlatency;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keyword: Push vs Pull Model
 *
 * PULL (polling): Consumer repeatedly asks the data source "do you have anything new?"
 * - Wastes CPU on empty polls.
 * - Latency is bounded by poll interval (can be 1 ms or more).
 *
 * PUSH (event-driven): Producer notifies consumers the instant data is available.
 * - Zero wasted polls.
 * - Latency is driven by event delivery, not poll interval.
 * - Ownership, publication, and consumption are clearly separated.
 *
 * This demo measures notification latency for both patterns under the same
 * event rate and contrasts CPU efficiency.
 */
public class PushVsPullDemo {

    record TickEvent(String symbol, double price, long publishedNs) {}

    // =========================================================================
    // PULL pattern: consumer polls a shared slot at a fixed interval
    // =========================================================================
    static class PullBroker {
        private final AtomicReference<TickEvent> latestTick = new AtomicReference<>();

        void publish(TickEvent event) { latestTick.set(event); }

        /** Blocking poll: waits up to {@code pollIntervalMs} between checks. */
        TickEvent poll(long pollIntervalMs) throws InterruptedException {
            TickEvent tick;
            do {
                tick = latestTick.getAndSet(null);
                if (tick == null) Thread.sleep(pollIntervalMs);
            } while (tick == null);
            return tick;
        }
    }

    // =========================================================================
    // PUSH pattern: producer calls registered listener directly
    // =========================================================================
    interface TickListener {
        void onTick(TickEvent event);
    }

    static class PushBroker {
        private final List<TickListener> listeners = new CopyOnWriteArrayList<>();

        void subscribe(TickListener listener) { listeners.add(listener); }

        /** Ownership: broker is sole publisher; consumers only receive, never fetch. */
        void publish(TickEvent event) {
            for (TickListener l : listeners) l.onTick(event);
        }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        int EVENT_COUNT = 10_000;
        String SYMBOL = "MSFT";

        // ---------------------------------------------------------------------
        // PULL benchmark
        // ---------------------------------------------------------------------
        PullBroker pullBroker = new PullBroker();
        AtomicLong pullTotalLatencyNs = new AtomicLong();
        CountDownLatch pullLatch = new CountDownLatch(EVENT_COUNT);

        Thread pullConsumer = Thread.ofPlatform().name("pull-consumer").start(() -> {
            try {
                for (int i = 0; i < EVENT_COUNT; i++) {
                    TickEvent e = pullBroker.poll(1);  // poll every 1 ms
                    pullTotalLatencyNs.addAndGet(System.nanoTime() - e.publishedNs());
                    pullLatch.countDown();
                }
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });

        Thread pullProducer = Thread.ofPlatform().name("pull-producer").start(() -> {
            for (int i = 0; i < EVENT_COUNT; i++) {
                pullBroker.publish(new TickEvent(SYMBOL, 200.0 + i * 0.001, System.nanoTime()));
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            }
        });

        pullLatch.await(60, TimeUnit.SECONDS);
        pullProducer.interrupt(); pullConsumer.interrupt();

        long pullAvgNs = pullTotalLatencyNs.get() / EVENT_COUNT;

        // ---------------------------------------------------------------------
        // PUSH benchmark
        // ---------------------------------------------------------------------
        PushBroker pushBroker = new PushBroker();
        AtomicLong pushTotalLatencyNs = new AtomicLong();
        CountDownLatch pushLatch = new CountDownLatch(EVENT_COUNT);

        pushBroker.subscribe(event -> {
            pushTotalLatencyNs.addAndGet(System.nanoTime() - event.publishedNs());
            pushLatch.countDown();
        });

        Thread pushProducer = Thread.ofPlatform().name("push-producer").start(() -> {
            for (int i = 0; i < EVENT_COUNT; i++) {
                pushBroker.publish(new TickEvent(SYMBOL, 200.0 + i * 0.001, System.nanoTime()));
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            }
        });

        pushLatch.await(60, TimeUnit.SECONDS);
        pushProducer.interrupt();

        long pushAvgNs = pushTotalLatencyNs.get() / EVENT_COUNT;

        // ---------------------------------------------------------------------
        System.out.println("=== Push vs Pull Notification Latency ===");
        System.out.printf("PULL avg latency : %,10d ns  (~%.2f ms)%n", pullAvgNs, pullAvgNs / 1e6);
        System.out.printf("PUSH avg latency : %,10d ns  (~%.3f ms)%n", pushAvgNs, pushAvgNs / 1e6);
        System.out.printf("Latency ratio    : %.0fx improvement with PUSH%n",
                (double) pullAvgNs / Math.max(pushAvgNs, 1));
        System.out.println();
        System.out.println("PUSH eliminates wasted polls and decouples producer from consumer.");
        System.out.println("Clear ownership: broker OWNS data, consumers SUBSCRIBE, never fetch.");
    }
}