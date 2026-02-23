package com.ankur.design.lowlatency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Keyword: Messaging Fabric (Pub/Sub)
 *
 * A high-performance in-process Pub/Sub bus that models the core behaviour of
 * production messaging fabrics (TIBCO RV, 29West LBM, Aeron, Kafka).
 *
 * Features:
 * - Topic-based routing: publishers and subscribers are fully decoupled.
 * - Fan-out: a single publish is delivered to ALL subscribers of a topic.
 * - Back-pressure: slow consumers receive events on a bounded async queue.
 * - DevOps observability: per-topic message counts and latency stats.
 *
 * Topics follow a hierarchical naming convention: ASSET.EXCHANGE.SYMBOL
 * e.g. "EQUITY.NYSE.AAPL", "FX.CME.EURUSD"
 */
public class MessagingFabricDemo {

    record Message(String topic, String payload, long publishedNs) {}

    // =========================================================================
    // Topic subscription holder with per-subscriber async delivery queue
    // =========================================================================
    static final class Subscription {
        final String subscriberId;
        final Consumer<Message> handler;
        final BlockingQueue<Message> queue;
        final Thread deliveryThread;
        final AtomicLong received = new AtomicLong();
        final AtomicLong totalLatencyNs = new AtomicLong();
        volatile boolean active = true;

        Subscription(String subscriberId, Consumer<Message> handler, int queueCapacity) {
            this.subscriberId = subscriberId;
            this.handler      = handler;
            this.queue        = new ArrayBlockingQueue<>(queueCapacity);
            this.deliveryThread = Thread.ofPlatform().name("sub-" + subscriberId).daemon(true).start(this::deliverLoop);
        }

        private void deliverLoop() {
            while (active || !queue.isEmpty()) {
                try {
                    Message m = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (m != null) {
                        handler.accept(m);
                        received.incrementAndGet();
                        totalLatencyNs.addAndGet(System.nanoTime() - m.publishedNs());
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }

        long avgLatencyNs() {
            long r = received.get();
            return r == 0 ? 0 : totalLatencyNs.get() / r;
        }

        void stop() { active = false; }
    }

    // =========================================================================
    // The Messaging Fabric (topic router)
    // =========================================================================
    static final class MessagingFabric {
        // topic -> list of active subscriptions
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription>> topicMap
                = new ConcurrentHashMap<>();
        private final AtomicLong published = new AtomicLong();
        private final AtomicLong dropped   = new AtomicLong(); // back-pressure drops

        /**
         * Subscribe to a topic pattern. Supports exact match or wildcard suffix "*".
         * e.g. "EQUITY.NYSE.*" matches all NYSE equity symbols.
         */
        Subscription subscribe(String topicPattern, String subscriberId, Consumer<Message> handler) {
            Subscription sub = new Subscription(subscriberId, handler, 1024);
            topicMap.computeIfAbsent(topicPattern, k -> new CopyOnWriteArrayList<>()).add(sub);
            System.out.printf("[fabric] %s subscribed to '%s'%n", subscriberId, topicPattern);
            return sub;
        }

        /**
         * Publish a message to a topic. All matching subscriptions receive it.
         * Routing is O(topics) but amortised by CopyOnWriteArrayList read.
         */
        void publish(String topic, String payload) {
            Message msg = new Message(topic, payload, System.nanoTime());
            published.incrementAndGet();

            for (Map.Entry<String, CopyOnWriteArrayList<Subscription>> entry : topicMap.entrySet()) {
                if (matches(entry.getKey(), topic)) {
                    for (Subscription sub : entry.getValue()) {
                        if (!sub.queue.offer(msg)) {
                            dropped.incrementAndGet(); // consumer too slow — back pressure
                        }
                    }
                }
            }
        }

        /** Simple wildcard: exact match OR prefix match with trailing "*". */
        private boolean matches(String pattern, String topic) {
            if (pattern.endsWith("*")) {
                return topic.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return pattern.equals(topic);
        }

        void printStats(List<Subscription> subs) {
            System.out.println("\n=== Messaging Fabric Stats ===");
            System.out.printf("Messages published : %,d%n", published.get());
            System.out.printf("Messages dropped   : %,d (back pressure)%n", dropped.get());
            for (Subscription s : subs) {
                System.out.printf("  Subscriber %-20s received=%,6d  avg_latency=%,d ns%n",
                        s.subscriberId, s.received.get(), s.avgLatencyNs());
            }
        }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        MessagingFabric fabric = new MessagingFabric();

        // Multiple subscribers with different topic interests
        List<Subscription> subs = new ArrayList<>();
        subs.add(fabric.subscribe("EQUITY.NYSE.*",      "RiskEngine",    m -> {}));
        subs.add(fabric.subscribe("EQUITY.NASDAQ.*",    "AlgoStrategy",  m -> {}));
        subs.add(fabric.subscribe("EQUITY.*",           "MarketDataFeed",m -> {})); // all equities
        subs.add(fabric.subscribe("FX.CME.EURUSD",     "FxHedger",      m -> {}));
        subs.add(fabric.subscribe("EQUITY.NYSE.AAPL",  "AAPLArbitrage", m -> {}));

        System.out.println("\n--- Publishing market data events ---");

        String[] nyseSymbols   = {"AAPL", "IBM",  "GS",  "JPM"};
        String[] nasdaqSymbols = {"MSFT", "GOOG", "AMZN","META"};

        int PUBLISH_COUNT = 50_000;
        long start = System.nanoTime();

        for (int i = 0; i < PUBLISH_COUNT; i++) {
            // NYSE equities
            String nyseSym = nyseSymbols[i % nyseSymbols.length];
            fabric.publish("EQUITY.NYSE." + nyseSym,
                    String.format("{\"bid\":%.2f,\"ask\":%.2f}", 150.0 + i * 0.001, 150.05 + i * 0.001));

            // NASDAQ equities
            String nasdaqSym = nasdaqSymbols[i % nasdaqSymbols.length];
            fabric.publish("EQUITY.NASDAQ." + nasdaqSym,
                    String.format("{\"bid\":%.2f,\"ask\":%.2f}", 200.0 + i * 0.001, 200.05 + i * 0.001));

            // FX
            if (i % 10 == 0) {
                fabric.publish("FX.CME.EURUSD",
                        String.format("{\"rate\":%.5f}", 1.0850 + i * 0.00001));
            }
        }

        long publishNs = System.nanoTime() - start;

        // Allow async delivery to drain
        Thread.sleep(500);

        fabric.printStats(subs);
        System.out.printf("%nPublish throughput: %,.0f msgs/sec%n",
                PUBLISH_COUNT * 2.0 / (publishNs / 1e9));

        subs.forEach(Subscription::stop);
    }
}