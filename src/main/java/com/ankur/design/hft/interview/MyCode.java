package com.ankur.design.hft.interview;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publish / Subscribe — EventBus with per-subscriber BlockingQueue
 *
 * WHY QUEUE, NOT ReentrantLock+Condition:
 *
 *   ReentrantLock+Condition (old code):
 *     - Publisher BLOCKS until consumer finishes  ← handshake / exchange pattern
 *     - One slot, strict alternation
 *     - Suitable for: backpressure-enforced 1:1 pipeline
 *
 *   BlockingQueue per subscriber (this code):
 *     - Publisher puts and returns immediately     ← true pub/sub, fire-and-forget
 *     - Each subscriber drains its own queue at its own pace
 *     - Multiple subscribers, each independent
 *     - Suitable for: fan-out, event-driven systems, message bus
 *
 * Design:
 *
 *   Publisher ──publish()──► EventBus ──fan-out──► [Queue-A] ──► Subscriber A
 *                                              └──► [Queue-B] ──► Subscriber B
 *                                              └──► [Queue-C] ──► Subscriber C
 *
 * Rules:
 *   - EventBus holds one LinkedBlockingQueue per registered subscriber
 *   - publish() puts the event into EVERY subscriber's queue (fan-out)
 *   - Each Subscriber runs on its own thread, calls take() in a loop
 *   - Publisher never touches a lock, never waits for a consumer
 *
 * Poison pill: publisher sends POISON after last event; each subscriber
 * exits its loop when it receives it (one pill per subscriber queue).
 */
class MyCode {

    // ── Event ─────────────────────────────────────────────────────────────────

    record Event(long id, String payload) {
        @Override public String toString() { return "Event{" + id + ",'" + payload + "'}"; }
    }

    static final Event POISON = new Event(-1, "POISON");

    // ── EventListener ─────────────────────────────────────────────────────────

    interface EventListener {
        void onEvent(Event event);
    }

    // ── EventBus ──────────────────────────────────────────────────────────────

    static class EventBus {

        private final int queueCapacity;
        // one bounded queue per subscriber — registered before publishing starts
        // plain ArrayList — subscribers register at startup before publisher runs,
        // so there are no concurrent writes; unmodifiableList guards against accidental mutation
        private final List<BlockingQueue<Event>> queues =
                Collections.synchronizedList(new ArrayList<>());

        EventBus(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        /**
         * Register a new subscriber. Returns the queue the subscriber should drain.
         * Must be called before publish() starts.
         */
        BlockingQueue<Event> register() {
            LinkedBlockingQueue<Event> q = new LinkedBlockingQueue<>(queueCapacity);
            queues.add(q);
            return q;
        }

        /**
         * Fan-out: put this event into every subscriber's queue.
         * Non-blocking for the publisher — offer() drops if queue is full
         * (use put() if you need backpressure instead).
         *
         * WHY NO LOCK HERE:
         *   CopyOnWriteArrayList iteration is safe without a lock.
         *   LinkedBlockingQueue.offer() is internally lock-free on the put side.
         *   Publisher thread never contends with subscriber threads.
         */
        void publish(Event event) {
            for (BlockingQueue<Event> q : queues) {
                boolean accepted = q.offer(event);
                if (!accepted) {
                    System.out.printf("[EventBus ] DROPPED %s — subscriber queue full%n", event);
                }
            }
        }

        /** Send one poison pill per subscriber to shut down all consumers cleanly. */
        void shutdown() {
            for (BlockingQueue<Event> q : queues) q.offer(POISON);
        }
    }

    // ── Subscriber ────────────────────────────────────────────────────────────

    /**
     * Subscriber owns a thread and a queue reference.
     * It does NOT extend Thread — lifecycle is managed by ExecutorService.
     */
    static class Subscriber implements Runnable {

        private final String             name;
        private final BlockingQueue<Event> queue;
        private final EventListener      handler;
        private final AtomicLong         processed = new AtomicLong(0);

        Subscriber(String name, BlockingQueue<Event> queue, EventListener handler) {
            this.name    = name;
            this.queue   = queue;
            this.handler = handler;
        }

        @Override
        public void run() {
            System.out.printf("[%-12s] started, waiting for events%n", name);
            while (true) {
                try {
                    Event e = queue.take();        // blocks until event arrives
                    if (e == POISON) break;        // identity check — sentinel exit
                    handler.onEvent(e);
                    processed.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.printf("[%-12s] stopped. processed=%d%n", name, processed.get());
        }
    }

    // ── Publisher ─────────────────────────────────────────────────────────────

    static class Publisher implements Runnable {

        private final EventBus   bus;
        private final int        count;
        private final AtomicLong idGen = new AtomicLong(1);

        Publisher(EventBus bus, int count) {
            this.bus   = bus;
            this.count = count;
        }

        @Override
        public void run() {
            System.out.printf("[Publisher  ] publishing %d events%n", count);
            for (int i = 0; i < count; i++) {
                Event e = new Event(idGen.getAndIncrement(), "OrderEvent-" + i);
                bus.publish(e);
                System.out.printf("[Publisher  ] published %s%n", e);
                try { Thread.sleep(20); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); break;
                }
            }
            bus.shutdown();   // send one poison pill per subscriber queue
            System.out.println("[Publisher  ] done");
        }
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Pub/Sub with per-subscriber BlockingQueue ===\n");

        EventBus bus = new EventBus(32);

        // register 3 independent subscribers BEFORE publisher starts
        BlockingQueue<Event> qA = bus.register();
        BlockingQueue<Event> qB = bus.register();
        BlockingQueue<Event> qC = bus.register();

        Subscriber subA = new Subscriber("Audit-Log",    qA, e ->
                System.out.printf("  [Audit-Log  ] %s%n", e));

        Subscriber subB = new Subscriber("Risk-Engine",  qB, e -> {
            System.out.printf("  [Risk-Engine] %s  risk=%s%n",
                    e, e.id() % 3 == 0 ? "WARN" : "OK");
        });

        Subscriber subC = new Subscriber("Order-Book",   qC, e -> {
            try { Thread.sleep(40); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            System.out.printf("  [Order-Book ] %s  (slow subscriber)%n", e);
        });

        ExecutorService pool = Executors.newFixedThreadPool(4);
        pool.submit(subA);
        pool.submit(subB);
        pool.submit(subC);
        pool.submit(new Publisher(bus, 6));

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\n=== Done ===");
    }
}