package com.ankur.design.lowlatency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keyword: Order Router
 *
 * A Smart Order Router (SOR) that splits a parent order across multiple
 * execution venues to achieve best price / best execution.
 *
 * Design:
 * - Each venue is polled for its current best price and available liquidity.
 * - The router greedily fills from the cheapest venue first (for a buy order).
 * - Venue queries run concurrently via CompletableFuture to minimise latency.
 * - A deadline (timeout) is enforced so the router never blocks indefinitely.
 *
 * In production this would use NIO / kernel-bypass (e.g. OpenOnload / DPDK)
 * sockets; here we simulate with in-process latency.
 */
public class OrderRouter {

    record VenueQuote(String venueId, double price, long availableQty, long latencyNs) {}

    record FillResult(String venueId, long filledQty, double price) {}

    /** Simulated venue: returns a quote with a random price and latency. */
    static class TradingVenue {
        private final String id;
        private final double basePrice;
        private final long availableQty;
        private final long simulatedLatencyMs;

        TradingVenue(String id, double basePrice, long availableQty, long simulatedLatencyMs) {
            this.id = id;
            this.basePrice = basePrice;
            this.availableQty = availableQty;
            this.simulatedLatencyMs = simulatedLatencyMs;
        }

        VenueQuote fetchQuote(String symbol) throws InterruptedException {
            long start = System.nanoTime();
            Thread.sleep(simulatedLatencyMs);  // simulate network round-trip
            double price = basePrice + (Math.random() - 0.5) * 0.02;
            return new VenueQuote(id, price, availableQty, System.nanoTime() - start);
        }

        FillResult fill(String symbol, long qty, double limitPrice) {
            long filled = Math.min(qty, availableQty);
            return new FillResult(id, filled, basePrice);
        }
    }

    private final List<TradingVenue> venues;
    private final ExecutorService executor;
    private final AtomicLong routedOrders = new AtomicLong();

    public OrderRouter(List<TradingVenue> venues) {
        this.venues = venues;
        // Each venue gets its own dedicated thread to avoid head-of-line blocking
        this.executor = Executors.newFixedThreadPool(venues.size());
    }

    /**
     * Route a BUY order for {@code totalQty} shares of {@code symbol}.
     * Deadline: {@code timeoutMs} milliseconds to gather all venue quotes.
     */
    public List<FillResult> routeBuyOrder(String symbol, long totalQty, long timeoutMs) throws Exception {
        // 1. Fan-out: fetch quotes from all venues concurrently
        List<CompletableFuture<VenueQuote>> quoteFutures = new ArrayList<>();
        for (TradingVenue venue : venues) {
            CompletableFuture<VenueQuote> f = CompletableFuture.supplyAsync(() -> {
                try { return venue.fetchQuote(symbol); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
            }, executor);
            quoteFutures.add(f);
        }

        // 2. Collect quotes within deadline
        List<VenueQuote> quotes = new ArrayList<>();
        for (CompletableFuture<VenueQuote> f : quoteFutures) {
            try {
                VenueQuote q = f.get(timeoutMs, TimeUnit.MILLISECONDS);
                System.out.printf("  Quote from %-8s  price=%.4f  qty=%4d  latency=%dµs%n",
                        q.venueId(), q.price(), q.availableQty(), q.latencyNs() / 1000);
                quotes.add(q);
            } catch (TimeoutException e) {
                System.out.println("  Timeout waiting for quote — venue excluded");
            }
        }

        // 3. Sort by price ascending (cheapest venue first for a buy)
        quotes.sort(Comparator.comparingDouble(VenueQuote::price));

        // 4. Greedy fill: walk venues from cheapest until order is fully filled
        List<FillResult> fills = new ArrayList<>();
        long remaining = totalQty;
        for (VenueQuote q : quotes) {
            if (remaining <= 0) break;
            long toFill = Math.min(remaining, q.availableQty());
            TradingVenue venue = venues.stream().filter(v -> v.id.equals(q.venueId())).findFirst().orElseThrow();
            FillResult fill = venue.fill(symbol, toFill, q.price());
            fills.add(fill);
            remaining -= fill.filledQty();
            System.out.printf("  Fill  at %-8s  qty=%4d  price=%.4f  remaining=%d%n",
                    fill.venueId(), fill.filledQty(), fill.price(), remaining);
        }

        if (remaining > 0) {
            System.out.printf("  WARNING: %d shares could not be filled — insufficient liquidity%n", remaining);
        }

        routedOrders.incrementAndGet();
        return fills;
    }

    public void shutdown() { executor.shutdownNow(); }

    public static void main(String[] args) throws Exception {
        List<TradingVenue> venues = List.of(
                new TradingVenue("NYSE",   150.05, 300, 2),
                new TradingVenue("NASDAQ", 150.03, 500, 1),
                new TradingVenue("BATS",   150.07, 200, 3),
                new TradingVenue("IEX",    150.04, 400, 4)
        );

        OrderRouter router = new OrderRouter(venues);

        System.out.println("=== Smart Order Router — BUY 900 shares of AAPL ===");
        long start = System.nanoTime();
        List<FillResult> fills = router.routeBuyOrder("AAPL", 900, 50);
        long totalNs = System.nanoTime() - start;

        long totalFilled = fills.stream().mapToLong(FillResult::filledQty).sum();
        double vwap = fills.stream().mapToDouble(f -> f.filledQty() * f.price()).sum() / totalFilled;

        System.out.printf("%nTotal filled: %d  VWAP: %.4f  Router time: %d ms%n",
                totalFilled, vwap, totalNs / 1_000_000);

        router.shutdown();
    }
}