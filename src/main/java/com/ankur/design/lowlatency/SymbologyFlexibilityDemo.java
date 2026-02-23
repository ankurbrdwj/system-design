package com.ankur.design.lowlatency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keyword: Symbology Flexibility
 *
 * In low-latency systems, the universe of traded symbols (tickers) can span
 * hundreds of thousands of instruments. Symbology flexibility means:
 *
 * 1. PARTITIONING: distribute symbols across servers/threads by consistent hash.
 * 2. HOT-SYMBOL ISOLATION: high-volume tickers (AAPL, MSFT) get dedicated
 *    processing resources; cold symbols share a pool.
 * 3. DYNAMIC REBALANCING: add/remove servers and re-shard without downtime.
 * 4. CROSS-SYMBOL AGGREGATION: roll-up statistics across partitions efficiently.
 *
 * This mirrors how production systems handle e.g. options chains (100k+ symbols)
 * or crypto markets (50k+ trading pairs).
 */
public class SymbologyFlexibilityDemo {

    // =========================================================================
    // Symbol metadata
    // =========================================================================
    record SymbolInfo(String ticker, String exchange, String assetClass,
                      long avgDailyVolume, boolean hotSymbol) {}

    // =========================================================================
    // A single processing shard: owns a set of symbols, processes their events
    // =========================================================================
    static final class SymbolShard {
        final int shardId;
        final Set<String> ownedSymbols = ConcurrentHashMap.newKeySet();
        final ExecutorService executor;
        final AtomicLong eventsProcessed = new AtomicLong();
        final AtomicLong totalLatencyNs  = new AtomicLong();
        volatile boolean active = true;

        SymbolShard(int shardId, int threads) {
            this.shardId  = shardId;
            this.executor = Executors.newFixedThreadPool(threads,
                    r -> new Thread(r, "shard-" + shardId + "-t"));
        }

        void assign(String symbol) { ownedSymbols.add(symbol); }
        void unassign(String symbol) { ownedSymbols.remove(symbol); }

        /** Process a market-data event for a symbol this shard owns. */
        Future<Long> processEvent(String symbol, double price) {
            return executor.submit(() -> {
                long start = System.nanoTime();
                // Simulate per-symbol computation (risk, P&L, greeks)
                double result = Math.log(price) * price * 0.0001;
                long latency  = System.nanoTime() - start;
                eventsProcessed.incrementAndGet();
                totalLatencyNs.addAndGet(latency);
                return latency;
            });
        }

        void shutdown() { executor.shutdownNow(); active = false; }
        long avgLatencyNs() {
            long e = eventsProcessed.get();
            return e == 0 ? 0 : totalLatencyNs.get() / e;
        }
    }

    // =========================================================================
    // Consistent-hash partitioner with dynamic rebalancing
    // =========================================================================
    static final class SymbolPartitionManager {
        // Sorted ring for consistent hashing
        private final TreeMap<Integer, SymbolShard> ring = new TreeMap<>();
        private final Map<String, SymbolShard>      symbolToShard = new ConcurrentHashMap<>();
        private final Map<SymbolInfo, SymbolShard>  hotSymbolShards = new ConcurrentHashMap<>();
        private final AtomicInteger shardIdGen = new AtomicInteger();

        /** Add a new shard to the ring (scale-out). */
        SymbolShard addShard(int threads) {
            SymbolShard shard = new SymbolShard(shardIdGen.getAndIncrement(), threads);
            // Place shard at multiple points on the ring for even distribution
            for (int i = 0; i < 150; i++) {
                ring.put(hash(shard.shardId + "-" + i), shard);
            }
            System.out.printf("[partition] Added shard-%d to ring (ring size=%d)%n",
                    shard.shardId, ring.size());
            return shard;
        }

        /** Dedicate an exclusive shard to a single hot symbol. */
        void pinHotSymbol(SymbolInfo sym) {
            SymbolShard dedicated = new SymbolShard(shardIdGen.getAndIncrement(), 2);
            dedicated.assign(sym.ticker());
            symbolToShard.put(sym.ticker(), dedicated);
            hotSymbolShards.put(sym, dedicated);
            System.out.printf("[partition] Hot symbol %-6s pinned to dedicated shard-%d%n",
                    sym.ticker(), dedicated.shardId);
        }

        /** Register a symbol with the appropriate shard via consistent hash. */
        void registerSymbol(String symbol) {
            if (symbolToShard.containsKey(symbol)) return; // already pinned
            SymbolShard shard = shardFor(symbol);
            shard.assign(symbol);
            symbolToShard.put(symbol, shard);
        }

        /** Route an event to the correct shard (O(log N) ring lookup). */
        SymbolShard route(String symbol) {
            return symbolToShard.getOrDefault(symbol, shardFor(symbol));
        }

        /** Rebalance: migrate symbols from overloaded shard to a new one. */
        void rebalance(SymbolShard overloaded, SymbolShard newShard) {
            List<String> symbols = new ArrayList<>(overloaded.ownedSymbols);
            int half = symbols.size() / 2;
            System.out.printf("[partition] Rebalancing: migrating %d symbols from shard-%d to shard-%d%n",
                    half, overloaded.shardId, newShard.shardId);
            for (int i = 0; i < half; i++) {
                String sym = symbols.get(i);
                overloaded.unassign(sym);
                newShard.assign(sym);
                symbolToShard.put(sym, newShard);
            }
        }

        Collection<SymbolShard> allShards() {
            Set<SymbolShard> all = new LinkedHashSet<>(ring.values());
            all.addAll(hotSymbolShards.values());
            return all;
        }

        private SymbolShard shardFor(String symbol) {
            int h = hash(symbol);
            Map.Entry<Integer, SymbolShard> entry = ring.ceilingEntry(h);
            if (entry == null) entry = ring.firstEntry();
            return entry.getValue();
        }

        private int hash(String key) { return Math.abs(key.hashCode()); }
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        SymbolPartitionManager mgr = new SymbolPartitionManager();

        // Build a cluster of 3 general shards
        SymbolShard s0 = mgr.addShard(2);
        SymbolShard s1 = mgr.addShard(2);
        SymbolShard s2 = mgr.addShard(2);

        // Hot symbols get their own dedicated shards
        List<SymbolInfo> hotSymbols = List.of(
                new SymbolInfo("AAPL", "NASDAQ", "EQUITY", 80_000_000, true),
                new SymbolInfo("MSFT", "NASDAQ", "EQUITY", 60_000_000, true)
        );
        hotSymbols.forEach(mgr::pinHotSymbol);

        // Register 1000 cold symbols
        System.out.println("\n[partition] Registering 1000 symbols...");
        for (int i = 0; i < 1000; i++) {
            mgr.registerSymbol("SYM-" + i);
        }

        // Print shard distribution
        System.out.println("\n=== Shard Distribution ===");
        for (SymbolShard s : mgr.allShards()) {
            System.out.printf("  shard-%d  symbols=%4d%n", s.shardId, s.ownedSymbols.size());
        }

        // Simulate 100k events
        System.out.println("\n[processing] Simulating 100k events...");
        Random rng = new Random(42);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < 100_000; i++) {
            String symbol = (i % 100 == 0) ? "AAPL"
                          : (i % 100 == 1) ? "MSFT"
                          : "SYM-" + rng.nextInt(1000);
            double price = 100.0 + rng.nextDouble() * 50;
            futures.add(mgr.route(symbol).processEvent(symbol, price));
        }
        for (Future<Long> f : futures) f.get();

        System.out.println("\n=== Processing Stats per Shard ===");
        for (SymbolShard s : mgr.allShards()) {
            System.out.printf("  shard-%d  events=%,7d  avg_latency=%,5d ns%n",
                    s.shardId, s.eventsProcessed.get(), s.avgLatencyNs());
        }

        // Dynamic scale-out: add a shard and rebalance overloaded one
        System.out.println("\n[partition] Scale-out: adding shard and rebalancing...");
        SymbolShard newShard = mgr.addShard(2);
        mgr.rebalance(s0, newShard);

        System.out.println("\n=== After Rebalancing ===");
        System.out.printf("  shard-%d  symbols=%d%n", s0.shardId, s0.ownedSymbols.size());
        System.out.printf("  shard-%d  symbols=%d%n", newShard.shardId, newShard.ownedSymbols.size());

        mgr.allShards().forEach(SymbolShard::shutdown);
    }
}