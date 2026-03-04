package com.ankur.design.hft.interview;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Publisher / Subscriber — NO QUEUE
 * Thread coordination using ONE ReentrantLock + TWO Conditions only.
 *
 * TWO CONDITIONS ON ONE LOCK:
 *   eventReady    — Publisher signals → Subscriber wakes up to consume
 *   eventConsumed — Subscriber signals → Publisher wakes up to produce next
 *
 * HANDSHAKE SEQUENCE (strict alternation, one event at a time):
 *
 *   Publisher                          Subscriber
 *   ─────────────────────────────────────────────────────
 *   lock.lock()
 *   event = new Event(...)             lock.lock()
 *   eventReady.signal()  ─────────►   (was in eventReady.await())
 *   eventConsumed.await() ◄─────────  process event
 *   (woken after consumed)             eventConsumed.signal()
 *   lock.unlock()                      lock.unlock()
 *   repeat...
 *
 * WHY TWO CONDITIONS?
 *   With ONE condition (or synchronized wait/notify) both threads share the
 *   same wait set. signal() might wake the wrong thread (Publisher wakes
 *   Publisher, or Subscriber wakes Subscriber).
 *   Two conditions give each thread its own dedicated wait/signal channel.
 *
 * STATIC NESTED CLASS RULES (summary):
 *   RULE 1: No reference to outer class instance — instantiated independently.
 *   RULE 2: Can only access STATIC members of outer class.
 *   RULE 3: Can have its own static and non-static members.
 *   RULE 4: Access modifier controls visibility (public/package-private/private).
 *   RULE 5: Can extend classes and implement interfaces.
 *   RULE 6: No hidden outer-class reference → no GC leak risk (unlike inner class).
 *   RULE 7: Suitable for Singleton — class object is a stable lock monitor.
 */
class MyCode {

    // =========================================================================
    // EventDispatcher — Singleton, two-condition handshake, no queue
    // =========================================================================
    public static class EventDispatcher {

        // RULE 7 + volatile: double-checked locking requires volatile
        private static volatile EventDispatcher instance;

        // ONE lock, TWO conditions — each thread waits on its own condition
        private final ReentrantLock lock           = new ReentrantLock();
        private final Condition     eventReady     = lock.newCondition(); // Publisher→Subscriber
        private final Condition     eventConsumed  = lock.newCondition(); // Subscriber→Publisher

        // Shared slot — no queue, just one event at a time
        private Event   pendingEvent = null;
        private boolean consumed     = true;   // true = Publisher may write next event

        // RULE 3: non-static field in a static nested class — allowed
        private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

        private EventDispatcher() {}

        static EventDispatcher getInstance() {
            if (instance == null) {
                synchronized (EventDispatcher.class) {   // RULE 7
                    if (instance == null) {
                        instance = new EventDispatcher();
                    }
                }
            }
            return instance;
        }

        void addListener(EventListener listener) {
            listeners.add(listener);
        }

        // ── Called by Publisher ──────────────────────────────────────────────

        /**
         * Publisher places one event and signals the Subscriber.
         * Then WAITS on eventConsumed until Subscriber has processed it.
         * No queue — just a single shared slot.
         */
        void publish(Event event) throws InterruptedException {
            lock.lock();
            try {
                // Wait until the previous event has been consumed
                while (!consumed) {
                    eventConsumed.await();   // park Publisher; releases lock
                }
                // Slot is free — write the event
                pendingEvent = event;
                consumed     = false;

                // Wake the Subscriber
                eventReady.signal();

            } finally {
                lock.unlock();
            }
        }

        // ── Called by Subscriber ─────────────────────────────────────────────

        /**
         * Subscriber waits until an event is available.
         * Picks it up, then signals Publisher that the slot is free.
         */
        Event consume() throws InterruptedException {
            lock.lock();
            try {
                // Wait until Publisher has placed an event
                while (consumed) {
                    eventReady.await();   // park Subscriber; releases lock
                }
                Event e  = pendingEvent;
                pendingEvent = null;
                consumed     = true;

                // Wake the Publisher — slot is free for next event
                eventConsumed.signal();

                return e;
            } finally {
                lock.unlock();
            }
        }

        void notifyListeners(Event event) {
            for (EventListener l : listeners) {
                l.onEvent(event);
            }
        }
    }

    // =========================================================================
    // Event
    // =========================================================================
    static class Event {
        private final String payload;
        Event(String payload) { this.payload = payload; }
        public String getPayload() { return payload; }
        @Override public String toString() { return "Event{'" + payload + "'}"; }
    }

    // =========================================================================
    // Subscriber — RULE 5: extends Thread + implements EventListener
    // =========================================================================
    static class Subscribe extends Thread implements EventListener {
        private final EventDispatcher dispatcher;
        private volatile boolean      running = true;

        Subscribe(EventDispatcher dispatcher) {
            this.dispatcher = dispatcher;
            setName("Subscriber-Thread");
            dispatcher.addListener(this);
        }

        @Override
        public void run() {
            System.out.println("[Subscriber] Waiting for events...");
            while (running) {
                try {
                    // Blocks on eventReady.await() until Publisher signals
                    Event event = dispatcher.consume();
                    dispatcher.notifyListeners(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
            System.out.println("[Subscriber] Stopped.");
        }

        @Override
        public void onEvent(Event event) {
            System.out.printf("  [onEvent] %s consumed on %s%n",
                event.getPayload(), Thread.currentThread().getName());
        }

        void stopSubscriber() { running = false; interrupt(); }
    }

    // =========================================================================
    // Publisher — RULE 5: extends Thread
    // =========================================================================
    static class Publisher extends Thread {
        private final EventDispatcher dispatcher;
        private final int             count;

        Publisher(EventDispatcher dispatcher, int count) {
            this.dispatcher = dispatcher;
            this.count      = count;
            setName("Publisher-Thread");
        }

        @Override
        public void run() {
            System.out.printf("[Publisher] Will fire %d events%n", count);
            for (int i = 1; i <= count; i++) {
                try {
                    Event e = new Event("OrderEvent-" + i);
                    System.out.printf("[Publisher] Firing: %s%n", e.getPayload());
                    // Blocks on eventConsumed.await() until Subscriber consumes
                    dispatcher.publish(e);
                    System.out.printf("[Publisher] Confirmed consumed: %s%n", e.getPayload());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[Publisher] Done.");
        }
    }

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Two Conditions, No Queue ===\n");

        EventDispatcher dispatcher = EventDispatcher.getInstance();

        Subscribe subscriber = new Subscribe(dispatcher);
        Publisher publisher  = new Publisher(dispatcher, 5);

        subscriber.start();
        Thread.sleep(50);   // let subscriber reach eventReady.await() first
        publisher.start();

        publisher.join();
        Thread.sleep(100);
        subscriber.stopSubscriber();
        subscriber.join();

        System.out.println("\n=== Done ===");
    }
}

interface EventListener {
    void onEvent(MyCode.Event event);
}