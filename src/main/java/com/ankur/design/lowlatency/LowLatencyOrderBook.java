package com.ankur.design.lowlatency;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keyword: Low Latency Trading
 *
 * Demonstrates a lock-free order book using ConcurrentSkipListMap for
 * price-level aggregation. Measures ingress-to-egress latency in nanoseconds
 * targeting the 5-20 microsecond window described in the SLA.
 *
 * Key design choices:
 * - ConcurrentSkipListMap keeps price levels sorted without locks.
 * - AtomicLong for sequence IDs avoids synchronisation overhead.
 * - System.nanoTime() for high-resolution latency measurement.
 */
public class LowLatencyOrderBook {

    // BID side: highest price first (reversed natural order)
    private final ConcurrentSkipListMap<Long, Long> bids =
            new ConcurrentSkipListMap<>(java.util.Comparator.reverseOrder());

    // ASK side: lowest price first (natural order)
    private final ConcurrentSkipListMap<Long, Long> asks =
            new ConcurrentSkipListMap<>();

    private final AtomicLong sequenceId = new AtomicLong(0);
    private final String symbol;

    public LowLatencyOrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Add or update a price level. Price is in ticks (e.g. 1 tick = 0.01).
     * Returns the sequence ID assigned to this update.
     */
    public long addBid(long priceTicks, long quantity) {
        long seq = sequenceId.incrementAndGet();
        bids.merge(priceTicks, quantity, Long::sum);
        return seq;
    }

    public long addAsk(long priceTicks, long quantity) {
        long seq = sequenceId.incrementAndGet();
        asks.merge(priceTicks, quantity, Long::sum);
        return seq;
    }

    /** Best bid price (highest). Returns -1 if book is empty. */
    public long bestBid() {
        Map.Entry<Long, Long> entry = bids.firstEntry();
        return entry == null ? -1L : entry.getKey();
    }

    /** Best ask price (lowest). Returns Long.MAX_VALUE if book is empty. */
    public long bestAsk() {
        Map.Entry<Long, Long> entry = asks.firstEntry();
        return entry == null ? Long.MAX_VALUE : entry.getKey();
    }

    /** Mid-price in ticks. */
    public long midPrice() {
        return (bestBid() + bestAsk()) / 2;
    }

    /** Spread in ticks. */
    public long spread() {
        return bestAsk() - bestBid();
    }

    public static void main(String[] args) {
        LowLatencyOrderBook book = new LowLatencyOrderBook("AAPL");

        // Warm up JIT before measuring
        for (int i = 0; i < 10_000; i++) {
            book.addBid(15000 + i % 10, 100);
            book.addAsk(15010 + i % 10, 100);
        }
        // Clear and re-seed for clean measurement
        book.bids.clear();
        book.asks.clear();

        book.addBid(15000L, 500L);
        book.addBid(14990L, 300L);
        book.addAsk(15010L, 400L);
        book.addAsk(15020L, 200L);

        // Measure ingress-to-egress latency of a single update + read cycle
        int samples = 1_000_000;
        long[] latencies = new long[samples];
        for (int i = 0; i < samples; i++) {
            long ingressNs = System.nanoTime();
            book.addBid(14995L + (i % 5), 100L);
            long mid = book.midPrice();          // simulated egress computation
            long egressNs = System.nanoTime();
            latencies[i] = egressNs - ingressNs;
            discard(mid); // prevent JIT from eliding the computation
        }

        // Compute statistics
        long sum = 0, max = 0;
        for (long l : latencies) { sum += l; if (l > max) max = l; }
        long avg = sum / samples;

        java.util.Arrays.sort(latencies);
        long p99 = latencies[(int) (samples * 0.99)];
        long p999 = latencies[(int) (samples * 0.999)];

        System.out.printf("[%s] OrderBook Latency (ns)  avg=%d  p99=%d  p99.9=%d  max=%d%n",
                book.symbol, avg, p99, p999, max);
        System.out.printf("Best Bid: %d  Best Ask: %d  Mid: %d  Spread: %d ticks%n",
                book.bestBid(), book.bestAsk(), book.midPrice(), book.spread());
    }

    @SuppressWarnings("unused")
    private static <T> void discard(T ignored) {}
}
