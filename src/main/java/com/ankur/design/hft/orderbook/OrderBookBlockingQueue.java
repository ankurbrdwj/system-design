package com.ankur.design.hft.orderbook;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basic Blocking Queue for an Order Book.
 *
 * Architecture:
 *
 *   [FIX Gateway]  [REST API]  [Internal Strategy]
 *         │               │               │
 *         └───────────────┼───────────────┘
 *                         │  produce(order)
 *                   ┌─────▼──────┐
 *                   │  Order     │  ← ArrayBlockingQueue (bounded)
 *                   │  Queue     │    blocks producer if full (back-pressure)
 *                   └─────┬──────┘
 *                         │  take()
 *                  ┌──────▼───────┐
 *                  │  Matching    │  ← single consumer thread (no lock needed)
 *                  │  Engine      │    processes one order at a time, in order
 *                  └──────────────┘
 *
 * Why BlockingQueue for an order book?
 *  - Single consumer (matching engine) = no lock needed on the book itself
 *  - Bounded queue = back-pressure: producers slow down instead of OOM
 *  - BlockingQueue.put() blocks producer when full — natural flow control
 *  - BlockingQueue.take() blocks consumer when empty — no busy-spin
 *  - FIFO ordering guaranteed — critical for fairness (price-time priority)
 */
public class OrderBookBlockingQueue {

    // -------------------------------------------------------------------------
    // Order model
    // -------------------------------------------------------------------------

    public enum Side { BUY, SELL }

    public static class Order {
        final long   orderId;
        final String symbol;
        final Side   side;
        final long   price;    // price in ticks (e.g. 10050 = $100.50)
        final long   quantity;
        final long   timestamp;

        public Order(long orderId, String symbol, Side side, long price, long quantity) {
            this.orderId   = orderId;
            this.symbol    = symbol;
            this.side      = side;
            this.price     = price;
            this.quantity  = quantity;
            this.timestamp = System.nanoTime();
        }

        @Override
        public String toString() {
            return String.format("Order[id=%d %s %s px=%d qty=%d]",
                    orderId, side, symbol, price, quantity);
        }
    }

    // Sentinel — signals the matching engine to shut down gracefully
    private static final Order POISON_PILL =
            new Order(-1, "SHUTDOWN", Side.BUY, 0, 0);

    // -------------------------------------------------------------------------
    // Queue — bounded to apply back-pressure on fast producers
    // -------------------------------------------------------------------------

    private final BlockingQueue<Order> queue;

    public OrderBookBlockingQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    // -------------------------------------------------------------------------
    // Producer side — called by gateways / strategies
    // -------------------------------------------------------------------------

    /**
     * Submit an order. Blocks if the queue is full (back-pressure).
     * Use this when the producer should wait rather than drop.
     */
    public void submit(Order order) throws InterruptedException {
        queue.put(order);   // blocks if full
    }

    /**
     * Try to submit within a timeout. Returns false if queue stays full.
     * Use this when you'd rather reject than wait indefinitely.
     */
    public boolean trySubmit(Order order, long timeoutMs) throws InterruptedException {
        return queue.offer(order, timeoutMs, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // Matching Engine — single consumer thread
    // -------------------------------------------------------------------------

    public static class MatchingEngine implements Runnable {

        private final BlockingQueue<Order> queue;
        private final AtomicBoolean running = new AtomicBoolean(true);

        // Simple in-memory book (price → total quantity at that level)
        private final java.util.TreeMap<Long, Long> bids =
                new java.util.TreeMap<>(java.util.Comparator.reverseOrder()); // highest first
        private final java.util.TreeMap<Long, Long> asks =
                new java.util.TreeMap<>();                                     // lowest first

        public MatchingEngine(BlockingQueue<Order> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            System.out.println("[Engine] Matching engine started");
            while (running.get()) {
                try {
                    Order order = queue.take();  // blocks until an order arrives

                    if (order == POISON_PILL) {
                        System.out.println("[Engine] Shutdown signal received");
                        break;
                    }

                    process(order);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[Engine] Matching engine stopped");
        }

        private void process(Order order) {
            long latencyUs = (System.nanoTime() - order.timestamp) / 1_000;
            System.out.printf("[Engine] Processing %s | queue-to-engine latency: %d µs%n",
                    order, latencyUs);

            if (order.side == Side.BUY) {
                // Check if we can match against the ask side
                if (!asks.isEmpty() && asks.firstKey() <= order.price) {
                    long askPrice = asks.firstKey();
                    long askQty   = asks.get(askPrice);
                    long matched  = Math.min(order.quantity, askQty);
                    System.out.printf("[Engine] MATCH  BUY %d @ %d vs ASK %d @ %d%n",
                            matched, order.price, matched, askPrice);
                    if (askQty - matched == 0) asks.remove(askPrice);
                    else asks.put(askPrice, askQty - matched);
                } else {
                    // Rest in book
                    bids.merge(order.price, order.quantity, Long::sum);
                    System.out.printf("[Engine] RESTED BUY  %d @ %d | best bid=%d ask=%s%n",
                            order.quantity, order.price,
                            bids.isEmpty() ? 0 : bids.firstKey(),
                            asks.isEmpty() ? "-" : asks.firstKey());
                }
            } else {
                // SELL — check bid side
                if (!bids.isEmpty() && bids.firstKey() >= order.price) {
                    long bidPrice = bids.firstKey();
                    long bidQty   = bids.get(bidPrice);
                    long matched  = Math.min(order.quantity, bidQty);
                    System.out.printf("[Engine] MATCH  SELL %d @ %d vs BID %d @ %d%n",
                            matched, order.price, matched, bidPrice);
                    if (bidQty - matched == 0) bids.remove(bidPrice);
                    else bids.put(bidPrice, bidQty - matched);
                } else {
                    asks.merge(order.price, order.quantity, Long::sum);
                    System.out.printf("[Engine] RESTED SELL %d @ %d | best bid=%s ask=%d%n",
                            order.quantity, order.price,
                            bids.isEmpty() ? "-" : bids.firstKey(),
                            asks.isEmpty() ? 0 : asks.firstKey());
                }
            }
        }

        public void stop() {
            running.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // Demo
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {
        OrderBookBlockingQueue book = new OrderBookBlockingQueue(64);

        // Start the matching engine on its own thread
        MatchingEngine engine = new MatchingEngine(book.queue);
        Thread engineThread = Thread.ofPlatform()
                .name("matching-engine")
                .start(engine);

        // Simulate order flow from multiple producers
        long id = 1;

        // Resting orders on both sides
        book.submit(new Order(id++, "AAPL", Side.BUY,  15000, 100));  // BUY  100 @ $150.00
        book.submit(new Order(id++, "AAPL", Side.BUY,  14950, 200));  // BUY  200 @ $149.50
        book.submit(new Order(id++, "AAPL", Side.SELL, 15100, 150));  // SELL 150 @ $151.00

        // This SELL crosses the spread — should match against BUY @ 15000
        book.submit(new Order(id++, "AAPL", Side.SELL, 14900, 80));   // SELL  80 @ $149.00 → MATCH

        // This BUY crosses the spread — should match against SELL @ 15100
        book.submit(new Order(id++, "AAPL", Side.BUY,  15200, 50));   // BUY   50 @ $152.00 → MATCH

        // Shutdown
        book.queue.put(POISON_PILL);
        engineThread.join();

        System.out.printf("%nFinal queue depth: %d%n", book.queue.size());
    }
}