package com.ankur.design.hft.trading;

import java.util.*;

/**
 * C++ STL → Java Collections
 *
 * Direct mapping from the README:
 *
 *   std::vector          → ArrayList       (dynamic array, O(1) amortised append)
 *   std::map             → TreeMap         (red-black tree, sorted, O(log n) ops)
 *   std::unordered_map   → HashMap         (hash table, O(1) average ops)
 *   std::queue           → ArrayDeque      (double-ended queue, O(1) offer/poll)
 *   std::priority_queue  → PriorityQueue   (binary heap, O(log n) insert/poll)
 *
 * KEY LATENCY DIFFERENCE (from README):
 *   C++: STL stores objects BY VALUE → no heap allocation per element.
 *   Java: stores references + boxes primitives → extra heap allocation per element.
 *
 * In HFT Java we work around this with:
 *   - Primitive arrays (long[], double[]) instead of ArrayList<Long>
 *   - Object pooling to avoid per-element allocation
 *   - Off-heap structures (Chronicle Map, Agrona ManyToOneConcurrentArrayQueue)
 */
public class CppStlToJavaCollectionsDemo {

    // =========================================================================
    // 1. std::vector → ArrayList  (and why int[] beats ArrayList<Integer> in HFT)
    // =========================================================================
    static void vectorDemo() {
        System.out.println("--- std::vector → ArrayList / primitive array ---");

        // Java equivalent of std::vector<int>
        ArrayList<Integer> boxedList = new ArrayList<>();
        for (int i = 0; i < 1_000_000; i++) boxedList.add(i);  // boxing: new Integer(i) per element

        // HFT-preferred: primitive array — no boxing, contiguous memory (cache-friendly)
        int[] primitiveArray = new int[1_000_000];
        for (int i = 0; i < primitiveArray.length; i++) primitiveArray[i] = i;

        long start = System.nanoTime();
        long sum = 0;
        for (int v : boxedList) sum += v;  // unboxing on every access
        long boxedNs = System.nanoTime() - start;

        start = System.nanoTime();
        sum = 0;
        for (int v : primitiveArray) sum += v;
        long primitiveNs = System.nanoTime() - start;

        System.out.printf("  ArrayList<Integer> sum: %,d  time=%,d ns%n", sum, boxedNs);
        System.out.printf("  int[]              sum: %,d  time=%,d ns%n", sum, primitiveNs);
        System.out.printf("  Primitive array speedup: %.1fx%n%n", (double) boxedNs / primitiveNs);
    }

    // =========================================================================
    // 2. std::map → TreeMap  (sorted, red-black tree, O(log n))
    //    Classic use in trading: sorted order book price levels
    // =========================================================================
    static void mapDemo() {
        System.out.println("--- std::map → TreeMap (order book price levels) ---");

        // TreeMap keeps keys sorted — perfect for an order book
        TreeMap<Double, Long> bidSide = new TreeMap<>(Comparator.reverseOrder()); // highest price first
        TreeMap<Double, Long> askSide = new TreeMap<>();                           // lowest price first

        bidSide.put(149.90, 500L); bidSide.put(149.95, 300L); bidSide.put(149.80, 1000L);
        askSide.put(150.10, 200L); askSide.put(150.05, 400L); askSide.put(150.20, 800L);

        System.out.printf("  Best Bid: %.2f (qty=%d)%n",
                bidSide.firstKey(), bidSide.firstEntry().getValue());
        System.out.printf("  Best Ask: %.2f (qty=%d)%n",
                askSide.firstKey(), askSide.firstEntry().getValue());
        System.out.printf("  Spread:   %.2f%n%n", askSide.firstKey() - bidSide.firstKey());
    }

    // =========================================================================
    // 3. std::unordered_map → HashMap  (O(1) average, no ordering guarantee)
    //    Use: symbol → latest price lookups (hot path)
    // =========================================================================
    static void unorderedMapDemo() {
        System.out.println("--- std::unordered_map → HashMap (symbol price cache) ---");

        HashMap<String, Double> priceCache = new HashMap<>(128, 0.5f); // low load factor = fewer collisions
        String[] symbols = {"AAPL", "MSFT", "GOOG", "AMZN", "TSLA", "META", "NVDA", "NFLX"};
        double[] prices  = {150.0,  300.0,  140.0,  180.0,  250.0,  380.0,  900.0,  700.0};

        for (int i = 0; i < symbols.length; i++) priceCache.put(symbols[i], prices[i]);

        long start = System.nanoTime();
        int LOOKUPS = 10_000_000;
        double result = 0;
        for (int i = 0; i < LOOKUPS; i++) result += priceCache.get(symbols[i % symbols.length]);
        long hashMapNs = System.nanoTime() - start;

        System.out.printf("  %,d HashMap lookups: %,d ns total  (%d ns/lookup)  checksum=%.0f%n%n",
                LOOKUPS, hashMapNs, hashMapNs / LOOKUPS, result);
    }

    // =========================================================================
    // 4. std::queue → ArrayDeque  (O(1) offer/poll at both ends)
    //    Use: order queue, event queue between threads
    // =========================================================================
    static void queueDemo() {
        System.out.println("--- std::queue → ArrayDeque (order event queue) ---");

        record OrderEvent(int orderId, String symbol, char side, double price) {}

        ArrayDeque<OrderEvent> eventQueue = new ArrayDeque<>(1024);
        eventQueue.offer(new OrderEvent(1, "AAPL", 'B', 150.0));
        eventQueue.offer(new OrderEvent(2, "MSFT", 'S', 300.0));
        eventQueue.offer(new OrderEvent(3, "GOOG", 'B', 140.0));

        System.out.println("  Processing event queue (FIFO):");
        while (!eventQueue.isEmpty()) {
            OrderEvent e = eventQueue.poll();
            System.out.printf("    Order #%d: %s %s @ %.2f%n",
                    e.orderId(), e.symbol(), e.side() == 'B' ? "BUY" : "SELL", e.price());
        }
        System.out.println();
    }

    // =========================================================================
    // 5. std::priority_queue → PriorityQueue  (min/max heap, O(log n) insert/poll)
    //    Use: best-price order routing, task scheduling by priority
    // =========================================================================
    static void priorityQueueDemo() {
        System.out.println("--- std::priority_queue → PriorityQueue (best-ask routing) ---");

        record VenueQuote(String venue, double askPrice, long availableQty)
                implements Comparable<VenueQuote> {
            public int compareTo(VenueQuote other) {
                return Double.compare(this.askPrice, other.askPrice); // min-heap by price
            }
        }

        PriorityQueue<VenueQuote> bestAsk = new PriorityQueue<>();
        bestAsk.offer(new VenueQuote("BATS",   150.07, 200));
        bestAsk.offer(new VenueQuote("NASDAQ", 150.03, 500));
        bestAsk.offer(new VenueQuote("NYSE",   150.05, 300));
        bestAsk.offer(new VenueQuote("IEX",    150.04, 400));

        System.out.println("  Venues sorted by best ask price (cheapest first):");
        while (!bestAsk.isEmpty()) {
            VenueQuote q = bestAsk.poll();
            System.out.printf("    %-10s ask=%.2f  qty=%d%n", q.venue(), q.askPrice(), q.availableQty());
        }
    }

    // =========================================================================

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  C++ STL  →  Java Collections (HFT Context)");
        System.out.println("====================================================\n");
        vectorDemo();
        mapDemo();
        unorderedMapDemo();
        queueDemo();
        priorityQueueDemo();
    }
}