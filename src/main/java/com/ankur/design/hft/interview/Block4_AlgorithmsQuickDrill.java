package com.ankur.design.hft.interview;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Block 4: Algorithms Quick Drill
 *
 * The JD says "good math and strong knowledge of algorithms and complexity."
 * These are the most likely coding/whiteboard questions for a trading role.
 *
 * Interview questions answered here:
 *   Q1. "Implement VWAP over a rolling time window."
 *   Q2. "Implement a thread-safe rate limiter using a sliding window algorithm."
 *   Q3. "How would you track the best bid and ask across a live order book?"
 *
 * Sections:
 *   1. VWAP SLIDING WINDOW   — deque of (price, volume, timestamp); evict stale entries
 *   2. RATE LIMITER          — sliding window count; ConcurrentLinkedDeque for thread safety
 *   3. ORDER BOOK TOP-OF-BOOK — TreeMap for sorted price levels; O(log n) updates
 *   4. BIG-O CHEAT SHEET     — the collections Big-O table you must recite cold
 */
public class Block4_AlgorithmsQuickDrill {

    // =========================================================================
    // 1. VWAP — VOLUME WEIGHTED AVERAGE PRICE (ROLLING WINDOW)
    //
    // VWAP = Σ(price_i × volume_i) / Σ(volume_i)   over a time window
    //
    // Used by: algorithmic trading desks to benchmark execution quality.
    //          If you bought at < VWAP, you did better than the market average.
    //
    // Sliding window approach:
    //   - Maintain a deque of Trade records sorted by time (newest at tail).
    //   - On each new trade: add to tail, evict from head all trades older
    //     than the window. Maintain running pxVol and vol sums to avoid
    //     re-scanning the entire deque on every update → O(1) amortised update.
    //
    // Complexity:
    //   - update(): O(k) amortised where k = trades evicted (usually O(1))
    //   - vwap():   O(1)
    //   Space: O(N) where N = trades in the window
    // =========================================================================

    static final class VwapCalculator {
        private final long        windowMs;
        private final Deque<long[]> window;   // each entry: [priceRaw, volume, timestampMs]
                                               // priceRaw = price * 1_000_000 fixed-point
        private long runningPxVol = 0;         // Σ(priceRaw × volume)
        private long runningVol   = 0;         // Σ(volume)

        VwapCalculator(long windowMs) {
            this.windowMs = windowMs;
            this.window   = new ArrayDeque<>();
        }

        /** Add a new trade tick and evict stale entries. */
        void addTrade(double price, long volume) {
            long now      = System.currentTimeMillis();
            long priceRaw = (long)(price * 1_000_000);

            // Add new trade to tail
            window.addLast(new long[]{priceRaw, volume, now});
            runningPxVol += priceRaw * volume;
            runningVol   += volume;

            // Evict from head all trades outside the window — O(k) amortised
            while (!window.isEmpty() && now - window.peekFirst()[2] > windowMs) {
                long[] stale = window.removeFirst();
                runningPxVol -= stale[0] * stale[1];
                runningVol   -= stale[1];
            }
        }

        /** O(1) — running sums are maintained incrementally. */
        double vwap() {
            if (runningVol == 0) return Double.NaN;
            return (double) runningPxVol / runningVol / 1_000_000.0;
        }

        int windowSize() { return window.size(); }
    }

    // =========================================================================
    // 2. RATE LIMITER — SLIDING WINDOW COUNT
    //
    // Problem: exchanges impose throttle limits (e.g. max 100 orders/second).
    //          Violating the limit results in disconnection.
    //
    // Algorithm: sliding window count
    //   - Maintain a deque of timestamps of accepted requests.
    //   - On each request:
    //       1. Evict all timestamps older than windowMs from the head.
    //       2. If size < maxRequests → accept: add timestamp to tail, return true.
    //       3. Else → reject: return false.
    //
    // Complexity:
    //   - tryAcquire(): O(k) amortised where k = expired entries evicted
    //   - Space: O(maxRequests) — bounded
    //
    // Thread-safety: ConcurrentLinkedDeque + synchronized block for
    //   the check-then-act atomicity requirement (avoid TOCTOU race).
    //
    // Alternative: Token Bucket — pre-fill N tokens, refill at rate R/sec.
    //   Better for bursty traffic (allows short bursts up to bucket size).
    //   Sliding window is stricter: guarantees max N in any window of W ms.
    // =========================================================================

    static final class SlidingWindowRateLimiter {
        private final int                    maxRequests;
        private final long                   windowMs;
        private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();
        private final AtomicInteger          accepted = new AtomicInteger();
        private final AtomicInteger          rejected = new AtomicInteger();

        SlidingWindowRateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs    = windowMs;
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            synchronized (this) {
                // Evict expired timestamps from head
                while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                    timestamps.removeFirst();
                }
                if (timestamps.size() < maxRequests) {
                    timestamps.addLast(now);
                    accepted.incrementAndGet();
                    return true;
                }
                rejected.incrementAndGet();
                return false;
            }
        }

        int accepted() { return accepted.get(); }
        int rejected() { return rejected.get(); }
    }

    // =========================================================================
    // 3. ORDER BOOK TOP-OF-BOOK — TREEMAP FOR SORTED PRICE LEVELS
    //
    // Q: "How would you track the best bid and ask across a live order book?"
    //
    // Data structure choice:
    //   HashMap   — O(1) lookup but UNSORTED → can't find best bid/ask
    //   TreeMap   — O(log N) update + O(1) best bid (lastKey) / ask (firstKey)
    //               backed by red-black tree → same tree that ConcurrentSkipListMap uses
    //
    // Why not PriorityQueue?
    //   PriorityQueue only gives O(1) peek at the min/max, but cancel/update
    //   is O(N) because it has no index. TreeMap is better for order books
    //   because you need to find and cancel arbitrary price levels.
    //
    // Real production: Chronicle Map (off-heap) or a custom long[] array sorted
    //                  by price with binary search for maximum cache locality.
    //
    // Complexity:
    //   addOrder/cancelOrder : O(log N)   N = number of distinct price levels
    //   bestBid / bestAsk    : O(1)       TreeMap.lastKey() / firstKey()
    //   spread               : O(1)
    // =========================================================================

    static final class OrderBook {
        // bids: highest price is best → use descending TreeMap (or reverse key)
        private final TreeMap<Double, Long> bids = new TreeMap<>(Collections.reverseOrder());
        // asks: lowest price is best → natural ascending order
        private final TreeMap<Double, Long> asks = new TreeMap<>();

        void addBid(double price, long qty) {
            bids.merge(price, qty, Long::sum);
        }

        void addAsk(double price, long qty) {
            asks.merge(price, qty, Long::sum);
        }

        void cancelBid(double price, long qty) {
            bids.computeIfPresent(price, (k, v) -> v <= qty ? null : v - qty);
        }

        void cancelAsk(double price, long qty) {
            asks.computeIfPresent(price, (k, v) -> v <= qty ? null : v - qty);
        }

        /** Best bid = highest price a buyer will pay — O(1). */
        double bestBid() { return bids.isEmpty() ? Double.NaN : bids.firstKey(); }

        /** Best ask = lowest price a seller will accept — O(1). */
        double bestAsk() { return asks.isEmpty() ? Double.NaN : asks.firstKey(); }

        double spread()   { return bestAsk() - bestBid(); }

        long   bidQty(double price) { return bids.getOrDefault(price, 0L); }
        long   askQty(double price) { return asks.getOrDefault(price, 0L); }

        void printLadder(int levels) {
            List<Map.Entry<Double, Long>> askLevels = new ArrayList<>(asks.entrySet());
            // Print asks in descending order (top of ladder = highest ask first)
            for (int i = Math.min(levels, askLevels.size()) - 1; i >= 0; i--) {
                System.out.printf("  ASK  %8.2f   %6d%n",
                        askLevels.get(i).getKey(), askLevels.get(i).getValue());
            }
            System.out.println("  " + "-".repeat(26));
            int count = 0;
            for (Map.Entry<Double, Long> e : bids.entrySet()) {
                System.out.printf("  BID  %8.2f   %6d%n", e.getKey(), e.getValue());
                if (++count >= levels) break;
            }
        }
    }

    // =========================================================================
    // 4. BIG-O CHEAT SHEET (recite cold in interview)
    // =========================================================================

    static void printBigO() {
        System.out.println("  Collection          | get/contains | put/add | remove | sorted?");
        System.out.println("  " + "-".repeat(65));
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "HashMap",              "O(1) avg",   "O(1)",  "O(1)",  "No");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "LinkedHashMap",        "O(1) avg",   "O(1)",  "O(1)",  "No (insertion/access order)");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "TreeMap",              "O(log N)",   "O(log N)", "O(log N)", "Yes (natural/comparator)");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "ConcurrentHashMap",    "O(1) avg",   "O(1)",  "O(1)",  "No");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "ArrayList",            "O(1)",       "O(1)*", "O(N)",  "No  (*amortised)");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "ArrayDeque",           "O(1)",       "O(1)*", "O(1)",  "No  (prefer over LinkedList)");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "PriorityQueue",        "O(N)",       "O(log N)", "O(log N)", "Partial (heap)");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "HashSet",              "O(1) avg",   "O(1)",  "O(1)",  "No");
        System.out.printf("  %-20s| %-12s | %-7s | %-6s | %s%n",
                "TreeSet",              "O(log N)",   "O(log N)", "O(log N)", "Yes");
        System.out.println();
        System.out.println("  Key rules:");
        System.out.println("    ArrayDeque  > LinkedList  for stack/queue (cache-friendly, less GC)");
        System.out.println("    TreeMap     → order book price levels (sorted, O(log N))");
        System.out.println("    HashMap     → symbol lookups, position map (O(1))");
        System.out.println("    Binary search on sorted array → beats HashMap for cache locality");
        System.out.println("      when N is small (<128) and access pattern is sequential");
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== Block 4: Algorithms Quick Drill ===");
        System.out.println();

        // 1. VWAP
        System.out.println("--- 1. VWAP Sliding Window (5-second window) ---");
        VwapCalculator vwap = new VwapCalculator(5_000);

        double[][] trades = {
            {150.00, 1000}, {150.05, 500}, {150.10, 2000},
            {150.03, 800},  {150.08, 1200}
        };
        for (double[] t : trades) {
            vwap.addTrade(t[0], (long) t[1]);
            System.out.printf("  trade px=%.2f vol=%.0f  →  VWAP=%.4f  (window size=%d)%n",
                    t[0], t[1], vwap.vwap(), vwap.windowSize());
            Thread.sleep(1);
        }

        // Manual verification
        double manualPxVol = 150.00*1000 + 150.05*500 + 150.10*2000 + 150.03*800 + 150.08*1200;
        double manualVol   = 1000 + 500 + 2000 + 800 + 1200;
        System.out.printf("  Manual check VWAP = %.4f  (matches: %s)%n",
                manualPxVol / manualVol,
                Math.abs(vwap.vwap() - manualPxVol / manualVol) < 0.0001);
        System.out.println();

        // 2. Rate Limiter
        System.out.println("--- 2. Rate Limiter (sliding window, 5 req / 100ms) ---");
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 100);

        for (int i = 1; i <= 8; i++) {
            boolean ok = limiter.tryAcquire();
            System.out.printf("  Request %d: %s%n", i, ok ? "ALLOWED" : "REJECTED (rate limit)");
        }
        System.out.printf("  Accepted=%d  Rejected=%d%n", limiter.accepted(), limiter.rejected());

        System.out.println("  Waiting 110ms for window to slide...");
        Thread.sleep(110);

        for (int i = 9; i <= 11; i++) {
            boolean ok = limiter.tryAcquire();
            System.out.printf("  Request %d: %s%n", i, ok ? "ALLOWED" : "REJECTED");
        }
        System.out.printf("  Accepted=%d  Rejected=%d%n", limiter.accepted(), limiter.rejected());
        System.out.println();

        // 3. Order Book
        System.out.println("--- 3. Order Book Top-of-Book (TreeMap, O(log N) updates) ---");
        OrderBook book = new OrderBook();

        // Add bids
        book.addBid(150.20, 1000);
        book.addBid(150.15, 2000);
        book.addBid(150.10, 1500);

        // Add asks
        book.addAsk(150.25, 800);
        book.addAsk(150.30, 1200);
        book.addAsk(150.35, 600);

        System.out.println("  Initial ladder (2 levels):");
        System.out.println("       Price     Qty");
        book.printLadder(2);
        System.out.printf("  Best bid=%.2f  Best ask=%.2f  Spread=%.2f%n",
                book.bestBid(), book.bestAsk(), book.spread());

        // Cancel top bid
        book.cancelBid(150.20, 1000);
        System.out.printf("  After cancelling bid @ 150.20 → new best bid=%.2f%n", book.bestBid());
        System.out.println();

        // 4. Big-O cheat sheet
        System.out.println("--- 4. Big-O Cheat Sheet ---");
        printBigO();

        System.out.println("=== Algorithm Summary ===");
        System.out.println("  VWAP        : deque + running sums → O(1) amortised per trade");
        System.out.println("  Rate limiter: sliding window deque  → O(1) amortised per request");
        System.out.println("  Order book  : TreeMap               → O(log N) update, O(1) best bid/ask");
        System.out.println("  All three appear in real trading systems — know them cold.");
    }
}